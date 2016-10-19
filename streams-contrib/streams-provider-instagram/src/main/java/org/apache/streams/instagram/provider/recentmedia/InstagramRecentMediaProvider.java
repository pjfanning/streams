/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements. See the NOTICE file
distributed with this work for additional information
regarding copyright ownership. The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance *
http://www.apache.org/licenses/LICENSE-2.0 *
Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied. See the License for the
specific language governing permissions and limitations
under the License. */
package org.apache.streams.instagram.provider.recentmedia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Uninterruptibles;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.apache.streams.config.ComponentConfigurator;
import org.apache.streams.config.StreamsConfiguration;
import org.apache.streams.config.StreamsConfigurator;
import org.apache.streams.core.StreamsDatum;
import org.apache.streams.core.StreamsProvider;
import org.apache.streams.core.StreamsResultSet;
import org.apache.streams.instagram.*;
import org.apache.streams.instagram.provider.InstagramAbstractProvider;
import org.apache.streams.instagram.provider.InstagramDataCollector;
import org.apache.streams.instagram.provider.recentmedia.InstagramRecentMediaCollector;
import org.apache.streams.instagram.provider.userinfo.InstagramUserInfoProvider;
import org.apache.streams.jackson.StreamsJacksonMapper;
import org.apache.streams.util.ComponentUtils;
import org.apache.streams.util.SerializationUtil;
import org.jinstagram.entity.users.feed.MediaFeedData;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Instagram {@link org.apache.streams.core.StreamsProvider} that provides the recent media data for a group of users
 *
 * Retrieve recent posts from a list of user ids or names.
 *
 *  To use from command line:
 *
 *  Supply (at least) the following required configuration in application.conf:
 *
 *  instagram.clientKey
 *  instagram.usersInfo.authorizedTokens
 *  instagram.usersInfo.users
 *
 *  Launch using:
 *
 *  mvn exec:java -Dexec.mainClass=org.apache.streams.instagram.provider.recentmedia.InstagramRecentMediaProvider -Dexec.args="application.conf media.json"
 */
public class InstagramRecentMediaProvider extends InstagramAbstractProvider {

    public static final String STREAMS_ID = "InstagramRecentMediaProvider";

    private static final Logger LOGGER = LoggerFactory.getLogger(InstagramRecentMediaProvider.class);

    private static ObjectMapper MAPPER = StreamsJacksonMapper.getInstance();

    public InstagramRecentMediaProvider() {
    }

    public InstagramRecentMediaProvider(InstagramConfiguration config) {
        super(config);
    }

    @Override
    protected InstagramDataCollector getInstagramDataCollector() {
        return new InstagramRecentMediaCollector(super.dataQueue, super.config);
    }

    public static void main(String[] args) throws Exception {

        Preconditions.checkArgument(args.length >= 2);

        String configfile = args[0];
        String outfile = args[1];

        Config reference = ConfigFactory.load();
        File conf_file = new File(configfile);
        assert(conf_file.exists());
        Config conf = ConfigFactory.parseFileAnySyntax(conf_file, ConfigParseOptions.defaults().setAllowMissing(false));

        Config typesafe  = conf.withFallback(reference).resolve();

        StreamsConfiguration streamsConfiguration = StreamsConfigurator.detectConfiguration(typesafe);
        InstagramConfiguration config = new ComponentConfigurator<>(InstagramConfiguration.class).detectConfiguration(typesafe, "instagram");
        InstagramRecentMediaProvider provider = new InstagramRecentMediaProvider(config);

        PrintStream outStream = new PrintStream(new BufferedOutputStream(new FileOutputStream(outfile)));
        provider.prepare(config);
        provider.startStream();
        do {
            Uninterruptibles.sleepUninterruptibly(streamsConfiguration.getBatchFrequencyMs(), TimeUnit.MILLISECONDS);
            Iterator<StreamsDatum> iterator = provider.readCurrent().iterator();
            while(iterator.hasNext()) {
                StreamsDatum datum = iterator.next();
                String json;
                try {
                    json = MAPPER.writeValueAsString(datum.getDocument());
                    outStream.println(json);
                } catch (JsonProcessingException e) {
                    System.err.println(e.getMessage());
                }
            }
        } while( provider.isRunning());
        provider.cleanUp();
        outStream.flush();
    }
}
