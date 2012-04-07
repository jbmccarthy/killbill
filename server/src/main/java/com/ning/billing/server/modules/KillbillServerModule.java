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

package com.ning.billing.server.modules;

import com.ning.billing.account.glue.AccountModule;
import com.ning.billing.analytics.setup.AnalyticsModule;
import com.ning.billing.beatrix.glue.BeatrixModule;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.entitlement.glue.EntitlementModule;
import com.ning.billing.invoice.glue.InvoiceModule;
import com.ning.billing.jaxrs.resources.AccountResource;
import com.ning.billing.jaxrs.resources.BundleResource;
import com.ning.billing.jaxrs.resources.BundleTimelineResource;
import com.ning.billing.jaxrs.resources.InvoiceResource;
import com.ning.billing.jaxrs.resources.PaymentResource;
import com.ning.billing.jaxrs.resources.SubscriptionResource;
import com.ning.billing.payment.setup.PaymentModule;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.CallContextModule;
import com.ning.billing.util.glue.ClockModule;
import com.ning.billing.util.glue.FieldStoreModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.jetty.jdbi.guice.providers.DBIProvider;

import com.google.inject.AbstractModule;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;

public class KillbillServerModule extends AbstractModule
{
    @Override
    protected void configure() {
        configureDao();
        configureResources();
        installKillbillModules();
    }

    protected void configureDao() {
        bind(IDBI.class).to(DBI.class).asEagerSingleton();
        bind(DBI.class).toProvider(DBIProvider.class).asEagerSingleton();
    }

    protected void configureResources() {
        bind(AccountResource.class).asEagerSingleton();
        bind(BundleResource.class).asEagerSingleton();
        bind(SubscriptionResource.class).asEagerSingleton();
        bind(BundleTimelineResource.class).asEagerSingleton();
        bind(InvoiceResource.class).asEagerSingleton();
        bind(PaymentResource.class).asEagerSingleton();
    }

    protected void installKillbillModules() {
        install(new FieldStoreModule());
        install(new TagStoreModule());
        install(new CatalogModule());
    	install(new BusModule());
        install(new NotificationQueueModule());
        install(new CallContextModule());
        install(new AccountModule());
        install(new InvoiceModule());
        install(new EntitlementModule());
        install(new AnalyticsModule());
        install(new PaymentModule());
        install(new BeatrixModule());
        install(new ClockModule());
    }
}
