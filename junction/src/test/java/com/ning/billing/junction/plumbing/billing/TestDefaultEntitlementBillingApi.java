/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.junction.plumbing.billing;


import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.MockCatalog;
import com.ning.billing.catalog.MockCatalogService;
import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;

import com.ning.billing.entitlement.api.user.SubscriptionEvent;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.junction.api.BillingApi;
import com.ning.billing.lifecycle.KillbillService.ServiceException;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;

public class TestDefaultEntitlementBillingApi {
	private static final UUID zeroId = new UUID(0L,0L);
	private static final UUID oneId = new UUID(1L,0L);
	private static final UUID twoId = new UUID(2L,0L);

	private CatalogService catalogService;

	private List<SubscriptionBundle> bundles;
	private List<Subscription> subscriptions;
	private List<SubscriptionEvent> subscriptionTransitions;
	private EntitlementUserApi entitlementApi;


	private Clock clock;
	private Subscription subscription;
	private DateTime subscriptionStartDate;

	@BeforeSuite(alwaysRun=true)
	public void setup() throws ServiceException {
        catalogService = new MockCatalogService(new MockCatalog());
        clock = new ClockMock();
	}

	@BeforeMethod(alwaysRun=true)
	public void setupEveryTime() {
		bundles = new ArrayList<SubscriptionBundle>();
		final SubscriptionBundle bundle = new SubscriptionBundleData( zeroId,"TestKey", oneId,  clock.getUTCNow().minusDays(4), null);
		bundles.add(bundle);


		subscriptionTransitions = new LinkedList<SubscriptionEvent>();
		subscriptions = new LinkedList<Subscription>();

		SubscriptionBuilder builder = new SubscriptionBuilder();
		subscriptionStartDate = clock.getUTCNow().minusDays(3);
		builder.setStartDate(subscriptionStartDate).setId(oneId);
		subscription = new SubscriptionData(builder) {
		    @Override
            public List<SubscriptionEvent> getBillingTransitions() {
		    	return subscriptionTransitions;
		    }
		};

		subscriptions.add(subscription);

        entitlementApi = BrainDeadProxyFactory.createBrainDeadProxyFor(EntitlementUserApi.class);
        ((ZombieControl) entitlementApi).addResult("getBundlesForAccount", bundles);
        ((ZombieControl) entitlementApi).addResult("getSubscriptionsForBundle", subscriptions);
        ((ZombieControl) entitlementApi).addResult("getSubscriptionFromId", subscription);
        ((ZombieControl) entitlementApi).addResult("getBundleFromId", bundle);
        ((ZombieControl) entitlementApi).addResult("getBaseSubscription", subscription);

        assertTrue(true);
	}

    @Test(enabled=true, groups="fast")
	public void testBillingEventsEmpty() {
        UUID accountId = UUID.randomUUID();
        Account account = BrainDeadProxyFactory.createBrainDeadProxyFor(Account.class);
        ((ZombieControl) account).addResult("getId", accountId).addResult("getCurrency", Currency.USD);

        AccountUserApi accountApi = BrainDeadProxyFactory.createBrainDeadProxyFor(AccountUserApi.class);
        ((ZombieControl) accountApi).addResult("getAccountById", account);

        BillCycleDayCalculator bcdCalculator = new BillCycleDayCalculator(catalogService, entitlementApi);
        CallContextFactory factory = new DefaultCallContextFactory(clock);
		BillingApi api = new DefaultBillingApi(null, factory, accountApi, bcdCalculator, entitlementApi, catalogService);
		SortedSet<BillingEvent> events = api.getBillingEventsForAccountAndUpdateAccountBCD(new UUID(0L,0L));
		Assert.assertEquals(events.size(), 0);
	}

    @Test(enabled=true, groups="fast")
	public void testBillingEventsNoBillingPeriod() throws CatalogApiException {
		DateTime now = clock.getUTCNow();
		DateTime then = now.minusDays(1);
		Plan nextPlan = catalogService.getFullCatalog().findPlan("PickupTrialEvergreen10USD", now);
		PlanPhase nextPhase = nextPlan.getAllPhases()[0]; // The trial has no billing period
        PriceList nextPriceList = catalogService.getFullCatalog().findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME, now);
        

		SubscriptionEvent t = new DefaultSubscriptionEvent(new SubscriptionTransitionData(
				zeroId, oneId, twoId, EventType.API_USER, ApiEventType.CREATE, then, now, null, null, null, null,
				SubscriptionState.ACTIVE, nextPlan, nextPhase, nextPriceList, 1L, null, true), then);
		subscriptionTransitions.add(t);

        AccountUserApi accountApi = BrainDeadProxyFactory.createBrainDeadProxyFor(AccountUserApi.class);
        Account account = BrainDeadProxyFactory.createBrainDeadProxyFor(Account.class);
        ((ZombieControl)account).addResult("getBillCycleDay", 32);
        ((ZombieControl)account).addResult("getCurrency", Currency.USD);
        ((ZombieControl)accountApi).addResult("getAccountById", account);
		       
        BillCycleDayCalculator bcdCalculator = new BillCycleDayCalculator(catalogService, entitlementApi);
        CallContextFactory factory = new DefaultCallContextFactory(clock);
        BillingApi api = new DefaultBillingApi(null, factory, accountApi, bcdCalculator, entitlementApi, catalogService);
        SortedSet<BillingEvent> events = api.getBillingEventsForAccountAndUpdateAccountBCD(new UUID(0L,0L));

		checkFirstEvent(events, nextPlan, 32, oneId, now, nextPhase, ApiEventType.CREATE.toString());
	}

    @Test(enabled=true, groups="fast")
	public void testBillingEventsAnnual() throws CatalogApiException {
		DateTime now = clock.getUTCNow();
		DateTime then = now.minusDays(1);
		Plan nextPlan = catalogService.getFullCatalog().findPlan("PickupTrialEvergreen10USD", now);
		PlanPhase nextPhase = nextPlan.getAllPhases()[1];
		PriceList nextPriceList = catalogService.getFullCatalog().findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME, now);
		SubscriptionEvent t = new DefaultSubscriptionEvent(new SubscriptionTransitionData(
				zeroId, oneId, twoId, EventType.API_USER, ApiEventType.CREATE, then, now, null, null, null, null, SubscriptionState.ACTIVE, 
				nextPlan, nextPhase, nextPriceList, 1L, null, true), then);
		subscriptionTransitions.add(t);

		Account account = BrainDeadProxyFactory.createBrainDeadProxyFor(Account.class);
		((ZombieControl)account).addResult("getBillCycleDay", 1).addResult("getTimeZone", DateTimeZone.UTC)
                                .addResult("getCurrency", Currency.USD);

        ((MockCatalog)catalogService.getFullCatalog()).setBillingAlignment(BillingAlignment.SUBSCRIPTION);
        
		AccountUserApi accountApi = BrainDeadProxyFactory.createBrainDeadProxyFor(AccountUserApi.class);
		((ZombieControl)accountApi).addResult("getAccountById", account);

        BillCycleDayCalculator bcdCalculator = new BillCycleDayCalculator(catalogService, entitlementApi);
        CallContextFactory factory = new DefaultCallContextFactory(clock);
        BillingApi api = new DefaultBillingApi(null, factory, accountApi, bcdCalculator, entitlementApi, catalogService);
        SortedSet<BillingEvent> events = api.getBillingEventsForAccountAndUpdateAccountBCD(new UUID(0L,0L));

		checkFirstEvent(events, nextPlan, subscription.getStartDate().getDayOfMonth(), oneId, now, nextPhase, ApiEventType.CREATE.toString());
	}

    @Test(enabled=true, groups="fast")
	public void testBillingEventsMonthly() throws CatalogApiException {
		DateTime now = clock.getUTCNow();
		DateTime then = now.minusDays(1);
		Plan nextPlan = catalogService.getFullCatalog().findPlan("PickupTrialEvergreen10USD", now);
		PlanPhase nextPhase = nextPlan.getAllPhases()[1];
        PriceList nextPriceList = catalogService.getFullCatalog().findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME, now);
		SubscriptionEvent t = new DefaultSubscriptionEvent(new SubscriptionTransitionData(
				zeroId, oneId, twoId, EventType.API_USER, ApiEventType.CREATE, then, now, null, null, null, null, SubscriptionState.ACTIVE, nextPlan, nextPhase, nextPriceList,
				1L, null, true), then);
		subscriptionTransitions.add(t);

        AccountUserApi accountApi = BrainDeadProxyFactory.createBrainDeadProxyFor(AccountUserApi.class);
        Account account = BrainDeadProxyFactory.createBrainDeadProxyFor(Account.class);
        ((ZombieControl)account).addResult("getBillCycleDay", 32);
        ((ZombieControl)account).addResult("getCurrency", Currency.USD);
        ((ZombieControl)accountApi).addResult("getAccountById", account);

        ((MockCatalog)catalogService.getFullCatalog()).setBillingAlignment(BillingAlignment.ACCOUNT);
        
        BillCycleDayCalculator bcdCalculator = new BillCycleDayCalculator(catalogService, entitlementApi);
        CallContextFactory factory = new DefaultCallContextFactory(clock);
        BillingApi api = new DefaultBillingApi(null, factory, accountApi, bcdCalculator, entitlementApi, catalogService);
        SortedSet<BillingEvent> events = api.getBillingEventsForAccountAndUpdateAccountBCD(new UUID(0L,0L));

		checkFirstEvent(events, nextPlan, 32, oneId, now, nextPhase, ApiEventType.CREATE.toString());
	}

    @Test(enabled=true, groups="fast")
	public void testBillingEventsAddOn() throws CatalogApiException {
		DateTime now = clock.getUTCNow();
		DateTime then = now.minusDays(1);
		Plan nextPlan = catalogService.getFullCatalog().findPlan("Horn1USD", now);
		PlanPhase nextPhase = nextPlan.getAllPhases()[0];
        PriceList nextPriceList = catalogService.getFullCatalog().findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME, now);
		SubscriptionEvent t = new DefaultSubscriptionEvent(new SubscriptionTransitionData(
				zeroId, oneId, twoId, EventType.API_USER, ApiEventType.CREATE, then, now, null, null, null, null, SubscriptionState.ACTIVE, nextPlan, nextPhase, nextPriceList, 1L,
				null, true), then);
		subscriptionTransitions.add(t);

		Account account = BrainDeadProxyFactory.createBrainDeadProxyFor(Account.class);
		((ZombieControl)account).addResult("getBillCycleDay", 1).addResult("getTimeZone", DateTimeZone.UTC);
        ((ZombieControl)account).addResult("getCurrency", Currency.USD);

        AccountUserApi accountApi = BrainDeadProxyFactory.createBrainDeadProxyFor(AccountUserApi.class);
        ((ZombieControl)accountApi).addResult("getAccountById", account);
             
        ((MockCatalog)catalogService.getFullCatalog()).setBillingAlignment(BillingAlignment.BUNDLE);
        
        BillCycleDayCalculator bcdCalculator = new BillCycleDayCalculator(catalogService, entitlementApi);
        CallContextFactory factory = new DefaultCallContextFactory(clock);
        BillingApi api = new DefaultBillingApi(null, factory, accountApi, bcdCalculator, entitlementApi, catalogService);
        SortedSet<BillingEvent> events = api.getBillingEventsForAccountAndUpdateAccountBCD(new UUID(0L,0L));

		checkFirstEvent(events, nextPlan, subscription.getStartDate().plusDays(30).getDayOfMonth(), oneId, now, nextPhase, ApiEventType.CREATE.toString());
	}


	private void checkFirstEvent(SortedSet<BillingEvent> events, Plan nextPlan,
			int BCD, UUID id, DateTime time, PlanPhase nextPhase, String desc) throws CatalogApiException {
		Assert.assertEquals(events.size(), 1);
		BillingEvent event = events.first();

        if(nextPhase.getFixedPrice() != null) {
			Assert.assertEquals(nextPhase.getFixedPrice().getPrice(Currency.USD), event.getFixedPrice());
        } else {
            assertNull(event.getFixedPrice());
		}

		if(nextPhase.getRecurringPrice() != null) {
			Assert.assertEquals(nextPhase.getRecurringPrice().getPrice(Currency.USD), event.getRecurringPrice());
        } else {
            assertNull(event.getRecurringPrice());
		}

		Assert.assertEquals(BCD, event.getBillCycleDay());
		Assert.assertEquals(id, event.getSubscription().getId());
		Assert.assertEquals(time, event.getEffectiveDate());
		Assert.assertEquals(nextPhase, event.getPlanPhase());
		Assert.assertEquals(nextPlan, event.getPlan());
		Assert.assertEquals(nextPhase.getBillingPeriod(), event.getBillingPeriod());
		Assert.assertEquals(BillingModeType.IN_ADVANCE, event.getBillingMode());
		Assert.assertEquals(desc, event.getDescription());
	}
}