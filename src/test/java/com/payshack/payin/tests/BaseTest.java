package com.payshack.payin.tests;

import com.payshack.payin.api.PayinApiService;
import com.payshack.payin.utils.TestContext;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseTest {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected PayinApiService payinApi;

    @Before
    public void setUp() {
        payinApi = new PayinApiService();
        log.info("--- Starting: {} ---", getClass().getSimpleName());
    }

    @After
    public void tearDown() {
        TestContext.clear();
        log.info("--- Finished: {} ---", getClass().getSimpleName());
    }
}
