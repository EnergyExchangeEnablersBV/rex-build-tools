package net.powermatcher.core.auctioneer.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import net.powermatcher.api.data.ArrayBid;
import net.powermatcher.api.data.Bid;
import net.powermatcher.api.data.MarketBasis;
import net.powermatcher.api.data.Price;
import net.powermatcher.core.auctioneer.ObjectiveAuctioneer;
import net.powermatcher.core.bidcache.BidCache;
import net.powermatcher.mock.MockAgent;
import net.powermatcher.mock.MockContext;
import net.powermatcher.mock.MockObjectiveAgent;
import net.powermatcher.mock.SimpleSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * JUnit test for the {@link ObjectiveAuctioneer} class.
 *
 * @author FAN
 * @version 2.0
 */
public class ObjectiveAuctioneerTest {
    private final static int NR_AGENTS = 21;

    // This needs to be the same as the MarketBasis created in the Auctioneer
    private final MarketBasis marketBasis = new MarketBasis("electricity",
                                                            "EUR", 11, 0, 10);
    private Map<String, Object> auctioneerProperties;
    private MockContext context;

    private ObjectiveAuctioneer objectiveauctioneer;
    private MockAgent[] agents;

    private MockObjectiveAgent mockObjectiveAgent;

    private static final String OBJECTIVE_AUCTIONEER_NAME = "objectiveauctioneer";
    private static final String OBJECTIVE_AGENT_NAME = "objectiveagent";

    private BidCache aggregatedBids;

    @Before
    public void setUp() throws Exception {
        // Init Objective Agent
        mockObjectiveAgent = new MockObjectiveAgent(OBJECTIVE_AGENT_NAME);

        // Init Auctioneer
        objectiveauctioneer = new ObjectiveAuctioneer();
        objectiveauctioneer.addObjectiveEndpoint(mockObjectiveAgent);

        auctioneerProperties = new HashMap<String, Object>();
        auctioneerProperties.put("agentId", OBJECTIVE_AUCTIONEER_NAME);
        auctioneerProperties.put("clusterId", "DefaultCluster");
        auctioneerProperties.put("matcherId", OBJECTIVE_AUCTIONEER_NAME);
        auctioneerProperties.put("commodity", "electricity");
        auctioneerProperties.put("currency", "EUR");
        auctioneerProperties.put("priceSteps", "11");
        auctioneerProperties.put("minimumPrice", "0");
        auctioneerProperties.put("maximumPrice", "10");
        auctioneerProperties.put("bidTimeout", "600");
        auctioneerProperties.put("priceUpdateRate", "30");

        context = new MockContext(0);

        objectiveauctioneer.setContext(context);
        objectiveauctioneer.activate(auctioneerProperties);

        // Init MockAgents
        agents = new MockAgent[NR_AGENTS];
        for (int i = 0; i < NR_AGENTS; i++) {
            String agentId = "agent" + (i + 1);
            MockAgent newAgent = new MockAgent(agentId);
            newAgent.setDesiredParentId(OBJECTIVE_AUCTIONEER_NAME);
            agents[i] = newAgent;
        }

    }

    private void addAgents(int number) {
        for (int i = 0; i < number; i++) {
            new SimpleSession(agents[i], objectiveauctioneer).connect();
        }
    }

    private void removeAgents(int number) {
        for (int i = 0; i < number; i++) {
            agents[i].getSession().disconnect();
        }
    }

    @Test
    public void noEquilibriumOnDemandSide() {
        addAgents(3);
        // run 1
        agents[0].sendBid(new ArrayBid(marketBasis, 0, new double[] { 5, 5, 5,
                                                                     5, 5, 5, 5, 5, 5, 5, 5 }));
        agents[1].sendBid(new ArrayBid(marketBasis, 0, new double[] { 4, 4, 4,
                                                                     4, 4, 4, 4, 4, 4, 4, 4 }));
        agents[2].sendBid(new ArrayBid(marketBasis, 0, new double[] { 3, 3, 3,
                                                                     3, 3, 3, 3, 3, 3, 3, 3 }));
        context.getMockScheduler().doTaskOnce();
        assertEquals(10, agents[0].getLastPriceUpdate().getPrice()
                                  .getPriceValue(), 0);

        // run 2
        agents[0].sendBid(new ArrayBid(marketBasis, 0, new double[] { 5, 5, 5,
                                                                     5, 5, 5, 5, 5, 5, 5, 5 }));
        agents[1].sendBid(new ArrayBid(marketBasis, 0, new double[] { 4, 4, 4,
                                                                     4, 4, 2, 2, 2, 2, 2, 2 }));
        agents[2].sendBid(new ArrayBid(marketBasis, 0, new double[] { 3, 3, 3,
                                                                     3, 3, 1, 1, 1, 1, 1, 1 }));
        context.getMockScheduler().doTaskOnce();
        assertEquals(10, agents[0].getLastPriceUpdate().getPrice()
                                  .getPriceValue(), 0);
        removeAgents(3);
    }

    @Test
    public void objectiveAgentTest() {
        addAgents(3);
        agents[0].sendBid(new ArrayBid(marketBasis, 0, new double[] { 5, 5, 5,
                                                                     5, 5, 5, 5, 5, 5, 5, 5 }));
        agents[1].sendBid(new ArrayBid(marketBasis, 0, new double[] { 4, 4, 4,
                                                                     4, 4, 4, 4, 4, 4, 4, 4 }));
        agents[2].sendBid(new ArrayBid(marketBasis, 0, new double[] { 3, 3, 3,
                                                                     3, 3, 3, 3, 3, 3, 3, 3 }));
        context.getMockScheduler().doTaskOnce();

        objectiveauctioneer.addObjectiveEndpoint(mockObjectiveAgent);

        aggregatedBids = new BidCache(marketBasis);

        Bid aggregatedBid = aggregatedBids.aggregate().getAggregatedBid();

        Bid finalAggregatedBid = null;
        if (mockObjectiveAgent != null) {

            Bid aggregatedObjectiveBid = mockObjectiveAgent
                                                           .handleAggregateBid(aggregatedBid);

            finalAggregatedBid = aggregatedBid
                                              .aggregate(aggregatedObjectiveBid);

            // aggregate again with device agent bid.
            determinePrice(finalAggregatedBid);
        }

        assertArrayEquals(new double[] { 100.0, 50.0, 50.0, 0.0, 0.0, 0.0, 0.0,
                                        0.0, 0.0, 0.0, 0.0 },
                          ((ArrayBid) finalAggregatedBid).getDemand(), 0);
    }

    protected Price determinePrice(Bid aggregatedBid) {
        return aggregatedBid.calculateIntersection(0);
    }

    @Test
    public void noEquilibriumOnSupplySide() {
        addAgents(3);
        // run 1
        agents[0].sendBid(new ArrayBid(marketBasis, 0, new double[] { -5, -5,
                                                                     -5, -5, -5, -5, -5, -5, -5, -5, -5 }));
        agents[1].sendBid(new ArrayBid(marketBasis, 0, new double[] { -4, -4,
                                                                     -4, -4, -4, -4, -4, -4, -4, -4, -4 }));
        agents[2].sendBid(new ArrayBid(marketBasis, 0, new double[] { -3, -3,
                                                                     -3, -3, -3, -3, -3, -3, -3, -3, -3 }));
        context.getMockScheduler().doTaskOnce();
        assertEquals(3.0, agents[0].getLastPriceUpdate().getPrice()
                                   .getPriceValue(), 0);

        // run 2
        agents[0].sendBid(new ArrayBid(marketBasis, 0, new double[] { -5, -5,
                                                                     -5, -5, -5, -5, -5, -5, -5, -5, -5 }));
        agents[1].sendBid(new ArrayBid(marketBasis, 0, new double[] { -2, -2,
                                                                     -2, -2, -2, -4, -4, -4, -4, -4, -4 }));
        agents[2].sendBid(new ArrayBid(marketBasis, 0, new double[] { -1, -1,
                                                                     -1, -1, -1, -1, -1, -3, -3, -3, -3 }));
        context.getMockScheduler().doTaskOnce();
        assertEquals(3.0, agents[0].getLastPriceUpdate().getPrice()
                                   .getPriceValue(), 0);
        removeAgents(3);
    }

    @Test
    public void equilibriumSmallNumberOfBids() {
        addAgents(3);
        // run 1
        agents[0].sendBid(new ArrayBid(marketBasis, 0, new double[] { 5, 5, 5,
                                                                     5, 5, 5, 5, 5, 5, 5, 5 }));
        agents[1].sendBid(new ArrayBid(marketBasis, 0, new double[] { 4, 4, 4,
                                                                     4, 4, 0, 0, 0, 0, 0, 0 }));
        agents[2].sendBid(new ArrayBid(marketBasis, 0, new double[] { 0, 0, 0,
                                                                     0, 0, -5, -5, -5, -5, -5, -5 }));
        context.getMockScheduler().doTaskOnce();
        assertEquals(5, agents[0].getLastPriceUpdate().getPrice()
                                 .getPriceValue(), 0);

        // run 2
        agents[0].sendBid(new ArrayBid(marketBasis, 0, new double[] { -5, -5,
                                                                     -5, -5, -5, -5, -5, -5, -5, -5, -5 }));
        agents[1].sendBid(new ArrayBid(marketBasis, 0, new double[] { 0, 0, 0,
                                                                     0, 0, 0, 0, -4, -4, -4, -4 }));
        agents[2].sendBid(new ArrayBid(marketBasis, 0, new double[] { 9, 9, 9,
                                                                     9, 9, 9, 9, 9, 9, 9, 9 }));
        context.getMockScheduler().doTaskOnce();
        assertEquals(7, agents[0].getLastPriceUpdate().getPrice()
                                 .getPriceValue(), 0);
        removeAgents(3);
    }

    @Test
    public void equilibriumLargerSet() {
        addAgents(21);
        agents[0].sendBid(new ArrayBid(marketBasis, 0, new double[] { 5, 5, 5,
                                                                     5, 5, 5, 5, 5, 5, 5, 5 }));
        agents[1].sendBid(new ArrayBid(marketBasis, 0, new double[] { -4, -4,
                                                                     -4, -4, -4, -4, -4, -4, -4, -4, -4 }));
        agents[2].sendBid(new ArrayBid(marketBasis, 0, new double[] { 3, 3, 3,
                                                                     3, 3, 3, 3, 3, 3, 3, 3 }));
        agents[3].sendBid(new ArrayBid(marketBasis, 0, new double[] { -2, -2,
                                                                     -2, -2, -2, -2, -2, -2, -2, -2, -2 }));
        agents[4].sendBid(new ArrayBid(marketBasis, 0, new double[] { 1, 1, 1,
                                                                     1, 1, 1, 1, 1, 1, 1, 1 }));
        agents[5].sendBid(new ArrayBid(marketBasis, 0, new double[] { 0, 0, 0,
                                                                     0, 0, 0, 0, 0, 0, 0, 0 }));
        agents[6].sendBid(new ArrayBid(marketBasis, 0, new double[] { 5, 5, 5,
                                                                     5, 5, 0, 0, 0, 0, 0, 0 }));
        agents[7].sendBid(new ArrayBid(marketBasis, 0, new double[] { 0, 0, 0,
                                                                     0, 0, 0, -4, -4, -4, -4, -4 }));
        agents[8].sendBid(new ArrayBid(marketBasis, 0, new double[] { 3, 3, 3,
                                                                     3, 0, 0, 0, 0, 0, 0, 0 }));
        agents[9].sendBid(new ArrayBid(marketBasis, 0, new double[] { 0, 0, 0,
                                                                     -2, -2, -2, -2, -2, -2, -2, -2 }));
        agents[10].sendBid(new ArrayBid(marketBasis, 0, new double[] { 1, 1, 1,
                                                                      1, 1, 1, 1, 0, 0, 0, 0 }));
        agents[11].sendBid(new ArrayBid(marketBasis, 0, new double[] { 7, 7, 7,
                                                                      7, 7, 7, 7, 0, 0, 0, 0 }));
        agents[12].sendBid(new ArrayBid(marketBasis, 0, new double[] { 0, 0, 0,
                                                                      -6, -6, -6, -6, -6, -6, -6, -6 }));
        agents[13].sendBid(new ArrayBid(marketBasis, 0, new double[] { 8, 8, 8,
                                                                      8, 8, 8, 8, 8, 8, 8, 8 }));
        agents[14].sendBid(new ArrayBid(marketBasis, 0, new double[] { -9, -9,
                                                                      -9, -9, -9, -9, -9, -9, -9, -9, -9 }));
        agents[15].sendBid(new ArrayBid(marketBasis, 0, new double[] { 0, 0, 0,
                                                                      0, 0, 0, 0, 0, -8, -8, -8 }));
        agents[16].sendBid(new ArrayBid(marketBasis, 0, new double[] { 4, 4, 4,
                                                                      4, 4, 4, 3, 3, 3, 3, 3 }));
        agents[17].sendBid(new ArrayBid(marketBasis, 0, new double[] { 2, 2, 2,
                                                                      2, 1, 1, 1, 1, 0, 0, 0 }));
        agents[18].sendBid(new ArrayBid(marketBasis, 0, new double[] { -1, -1,
                                                                      -1, -1, -2, -2, -2, -2, -3, -3, -3 }));
        agents[19].sendBid(new ArrayBid(marketBasis, 0, new double[] { 6, 6, 6,
                                                                      6, 6, 6, 0, 0, 0, 0, 0 }));
        agents[20].sendBid(new ArrayBid(marketBasis, 0, new double[] { 8, 8, 8,
                                                                      8, 8, 8, 8, 8, 8, 8, 8 }));
        context.getMockScheduler().doTaskOnce();
        assertEquals(7, agents[0].getLastPriceUpdate().getPrice()
                                 .getPriceValue(), 0);
        removeAgents(21);
    }

    @After
    public void deactivateTest() {
        objectiveauctioneer
                           .removeObjectiveEndpoint(mockObjectiveAgent);
    }
}
