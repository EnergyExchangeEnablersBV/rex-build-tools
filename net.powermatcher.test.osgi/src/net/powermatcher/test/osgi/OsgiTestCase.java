package net.powermatcher.test.osgi;

import junit.framework.TestCase;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base case for OSGI testcases
 *
 * @author FAN
 * @version 2.0
 */
public abstract class OsgiTestCase
    extends TestCase {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final BundleContext context = FrameworkUtil.getBundle(getClass()).getBundleContext();

    protected ClusterHelper clusterHelper;

    /**
     * Setup tests, which cleans existing OSGI servers and gets reference to configuration admin.
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        clusterHelper = new ClusterHelper(context);
    }

    @Override
    protected void tearDown() throws Exception {
        clusterHelper.close();
        super.tearDown();
    }
}
