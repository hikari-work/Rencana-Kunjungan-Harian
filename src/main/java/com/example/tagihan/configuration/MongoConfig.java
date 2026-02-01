package com.example.tagihan.configuration;


import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.config.AbstractReactiveMongoConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configurable
@EnableMongoRepositories(basePackages = "com.example.tagihan.repository")
public class MongoConfig extends AbstractReactiveMongoConfiguration {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database:tagihan}")
    private String databaseName;

    @Override
    protected String getDatabaseName() {
        return databaseName;
    }
    @Bean
    @Override
    public MongoClient reactiveMongoClient() {
        ConnectionString connectionString = new ConnectionString(mongoUri);
        MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .applyToSocketSettings(builder -> builder.connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS))
                .applyToClusterSettings(builder -> builder.serverSelectionTimeout(30, TimeUnit.SECONDS))
                .applyToConnectionPoolSettings(builder -> builder.maxSize(100)
                        .minSize(5)
                        .maxWaitTime(30, TimeUnit.SECONDS)
                        .maxConnectionIdleTime(30, TimeUnit.SECONDS)
                        .maxConnectionLifeTime(30, TimeUnit.SECONDS))
                .retryWrites(true)
                .build();
        return MongoClients.create(mongoClientSettings);
    }

}
