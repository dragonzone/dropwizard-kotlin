# Dropwizard Async Non-Blocking [![Build Status](https://jenkins.dragon.zone/buildStatus/icon?job=dragonzone/dropwizard-async/master)](https://jenkins.dragon.zone/blue/organizations/jenkins/dragonzone%2Fdropwizard-async/activity?branch=master) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/zone.dragon.dropwizard/dropwizard-async/badge.svg)](https://maven-badges.herokuapp.com/maven-central/zone.dragon.dropwizard/dropwizard-async/)

This bundle adds support for resources methods to return `CompletionStage` and `ListenableFuture` types, which allow for fully non-blocking
handling of requests. 

To use this bundle, add it to your application in the initialize method:

    @Override
    public void initialize(Bootstrap<T> bootstrap) {
        bootstrap.addBundle(new AsyncBundle());
    }
