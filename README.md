# Dropwizard Kotlin [![Build Status](https://jenkins.dragon.zone/buildStatus/icon?job=dragonzone/dropwizard-kotlin/master)](https://jenkins.dragon.zone/blue/organizations/jenkins/dragonzone%2Fdropwizard-kotlin/activity?branch=master) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/zone.dragon.dropwizard/dropwizard-kotlin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/zone.dragon.dropwizard/dropwizard-kotlin/)

This bundle adds support for resource handlers written in kotlin to use 

To use this bundle, add it to your application in the initialize method:

    @Override
    public void initialize(Bootstrap<T> bootstrap) {
        bootstrap.addBundle(new KotlinBundle<>());
    }
