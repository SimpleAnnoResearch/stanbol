/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.stanbol.entityhub.core.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.PropertyOption;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.ReferenceStrategy;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.commons.stanboltools.offline.OfflineMode;
import org.apache.stanbol.entityhub.core.mapping.DefaultFieldMapperImpl;
import org.apache.stanbol.entityhub.core.mapping.FieldMappingUtils;
import org.apache.stanbol.entityhub.core.mapping.ValueConverterFactory;
import org.apache.stanbol.entityhub.core.model.DefaultSignFactory;
import org.apache.stanbol.entityhub.core.query.DefaultQueryFactory;
import org.apache.stanbol.entityhub.core.query.QueryResultListImpl;
import org.apache.stanbol.entityhub.core.utils.OsgiUtils;
import org.apache.stanbol.entityhub.servicesapi.mapping.FieldMapper;
import org.apache.stanbol.entityhub.servicesapi.mapping.FieldMapping;
import org.apache.stanbol.entityhub.servicesapi.model.Representation;
import org.apache.stanbol.entityhub.servicesapi.model.Sign;
import org.apache.stanbol.entityhub.servicesapi.model.rdf.RdfResourceEnum;
import org.apache.stanbol.entityhub.servicesapi.query.FieldQuery;
import org.apache.stanbol.entityhub.servicesapi.query.FieldQueryFactory;
import org.apache.stanbol.entityhub.servicesapi.query.QueryResultList;
import org.apache.stanbol.entityhub.servicesapi.site.EntityDereferencer;
import org.apache.stanbol.entityhub.servicesapi.site.EntitySearcher;
import org.apache.stanbol.entityhub.servicesapi.site.ReferencedSite;
import org.apache.stanbol.entityhub.servicesapi.site.ReferencedSiteException;
import org.apache.stanbol.entityhub.servicesapi.site.SiteConfiguration;
import org.apache.stanbol.entityhub.servicesapi.yard.Cache;
import org.apache.stanbol.entityhub.servicesapi.yard.CacheStrategy;
import org.apache.stanbol.entityhub.servicesapi.yard.Yard;
import org.apache.stanbol.entityhub.servicesapi.yard.YardException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentFactory;
import org.osgi.service.component.ComponentInstance;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This in the Default implementation of the {@link ReferencedSite} interface.
 * However this implementation forwards calls to methods defined within the
 * {@link EntityDereferencer} and {@link EntitySearcher} to sub components
 * (See the detailed description below).<p>
 * Each {@link ReferencedSite} with an {@link CacheStrategy} other than
 * {@link CacheStrategy#none} needs an associated {@link Cache}.
 * <p>
 * The Initialisation of the sub-components:
 * <ul>
 * <li> <b>{@link EntityDereferencer}:</b> Implementations of this interface are
 *      specific to the used protocol/technology of the referenced site.
 *      Because of that calls to methods defined in this interface are forwarded
 *      to an site specific instance of the {@link EntityDereferencer} interface
 *      as configured by the {@link SiteConfiguration#ENTITY_DEREFERENCER_TYPE} property.<br>
 *      During activation the the {@link BundleContext} is used to
 *      search for {@link ComponentFactory} with the configuration <code>
 *      "component.name= {@link ComponentContext#getProperties()}.get(
 *      {@link SiteConfiguration#ENTITY_DEREFERENCER_TYPE})</code>. This factory is used
 *      to create an instance of {@link EntityDereferencer}. <br>
 *      Note also, that the configuration of this instance that is covered
 *      by the {@link SiteConfiguration} interface are parsed to the
 *      {@link EntityDereferencer} instance.
 * <li> <b> {@link EntitySearcher}:</b> Implementations of this interface are
 *      also specific to the used protocol/technology of the referenced site.
 *      Because of that calls to methods defined in this interface are forwarded
 *      to an site specific instance of the {@link EntitySearcher} interface
 *      as configured by the {@link SiteConfiguration#ENTITY_SEARCHER_TYPE} property.<br>
 *      The initialisation of this instance works similar as described for the
 *      {@link EntityDereferencer}. However if the value of the {@link SiteConfiguration#ENTITY_SEARCHER_TYPE}
 *      is equals to {@link SiteConfiguration#ENTITY_DEREFERENCER_TYPE} or the
 *      {@link SiteConfiguration#ENTITY_SEARCHER_TYPE} is not defined at all, than the
 *      Dereferencer Instance is also used as {@link EntitySearcher}. If the
 *      according cast does not succeed, an {@link ConfigurationException} for the
 *      {@link SiteConfiguration#ENTITY_SEARCHER_TYPE} property is thrown.
 * <li> <b>{@link Cache}: </b> An instance of a {@link Cache} is used to
 *      cache {@link Representation}s loaded form the Site. A cache is a wrapper
 *      over a {@link Yard} instance that allows to configure what data are
 *      stored for each representation cached form this referenced site. A
 *      {@link ServiceTracker} is used for managing the dependency with the cache.
 *      So if a cache is no longer available a referenced site can still be used -
 *      only the local cache can not be used to retrieve entity representations.
 * </ul>
 *
 * @author Rupert Westenthaler
 *
 */
@Component(
        name="org.apache.stanbol.entityhub.site.referencedSite",
        configurationFactory=true,
        policy=ConfigurationPolicy.REQUIRE, //the baseUri is required!
        specVersion="1.1",
        metatype = true,
        immediate = true
        )
@Service(value=ReferencedSite.class)
@Properties(value={
        @Property(name=SiteConfiguration.ID,value="dbpedia"),
        @Property(name=SiteConfiguration.NAME,value="DB Pedia"),
        @Property(name=SiteConfiguration.DESCRIPTION, value="The OLD Endpoint for Wikipedia"),
        /*
         * TODO: can't use Integer.MAX_VALUE here, because I get a NumberFormatException
         * in den maven scr plugin. For now use a big number instead
         */
        @Property(name=SiteConfiguration.ENTITY_PREFIX, cardinality=10000, value={
                "http://dbpedia.org/resource/","http://dbpedia.org/ontology/"
        }),
        @Property(name=SiteConfiguration.ACCESS_URI, value="http://dbpedia.org/sparql/"),
        @Property(name=SiteConfiguration.ENTITY_DEREFERENCER_TYPE,
            options={
                @PropertyOption(
                        value='%'+SiteConfiguration.ENTITY_DEREFERENCER_TYPE+".option.none",
                        name=""),
                @PropertyOption(
                    value='%'+SiteConfiguration.ENTITY_DEREFERENCER_TYPE+".option.sparql",
                    name="org.apache.stanbol.entityhub.dereferencer.SparqlDereferencer"),
                @PropertyOption(
                        value='%'+SiteConfiguration.ENTITY_DEREFERENCER_TYPE+".option.coolUri",
                        name="org.apache.stanbol.entityhub.dereferencer.CoolUriDereferencer")
            },value="org.apache.stanbol.entityhub.dereferencer.SparqlDereferencer"),
        @Property(name=SiteConfiguration.QUERY_URI, value="http://dbpedia.org/sparql"), //the deri server has better performance
        @Property(name=SiteConfiguration.ENTITY_SEARCHER_TYPE,
            options={
                @PropertyOption(
                        value='%'+SiteConfiguration.ENTITY_SEARCHER_TYPE+".option.none",
                        name=""),
                @PropertyOption(
                    value='%'+SiteConfiguration.ENTITY_SEARCHER_TYPE+".option.sparql",
                    name="org.apache.stanbol.entityhub.searcher.SparqlSearcher"),
                @PropertyOption(
                        value='%'+SiteConfiguration.ENTITY_SEARCHER_TYPE+".option.sparql-virtuoso",
                        name="org.apache.stanbol.entityhub.searcher.VirtuosoSearcher"),
                @PropertyOption(
                        value='%'+SiteConfiguration.ENTITY_SEARCHER_TYPE+".option.sparql-larq",
                        name="org.apache.stanbol.entityhub.searcher.LarqSearcher")
            },value="org.apache.stanbol.entityhub.searcher.VirtuosoSearcher"),
        @Property(name=SiteConfiguration.DEFAULT_SYMBOL_STATE,
            options={
                @PropertyOption( //seems, that name and value are exchanged ...
                        value='%'+SiteConfiguration.DEFAULT_SYMBOL_STATE+".option.proposed",
                        name="proposed"),
                @PropertyOption(
                        value='%'+SiteConfiguration.DEFAULT_SYMBOL_STATE+".option.active",
                        name="active")
                //the other states make no sense for new symbols
            }, value="proposed"),
        @Property(name=SiteConfiguration.DEFAULT_MAPPING_STATE,
            options={
                @PropertyOption(
                        value='%'+SiteConfiguration.DEFAULT_MAPPING_STATE+".option.proposed",
                        name="proposed"),
                @PropertyOption(
                        value='%'+SiteConfiguration.DEFAULT_MAPPING_STATE+".option.confirmed",
                        name="confirmed")
                //the other states make no sense for new symbols
            }, value="proposed"),
        @Property(name=SiteConfiguration.DEFAULT_EXPIRE_DURATION,
            options={
                @PropertyOption(
                        value='%'+SiteConfiguration.DEFAULT_EXPIRE_DURATION+".option.oneMonth",
                        name=""+(1000L*60*60*24*30)),
                @PropertyOption(
                        value='%'+SiteConfiguration.DEFAULT_EXPIRE_DURATION+".option.halfYear",
                        name=""+(1000L*60*60*24*183)),
                @PropertyOption(
                        value='%'+SiteConfiguration.DEFAULT_EXPIRE_DURATION+".option.oneYear",
                        name=""+(1000L*60*60*24*365)),
                @PropertyOption(
                        value='%'+SiteConfiguration.DEFAULT_EXPIRE_DURATION+".option.none",
                        name="0")
            }, value="0"),
        @Property(name=SiteConfiguration.CACHE_STRATEGY,
            options={
                @PropertyOption(
                        value='%'+SiteConfiguration.CACHE_STRATEGY+".option.none",
                        name="none"),
                @PropertyOption(
                        value='%'+SiteConfiguration.CACHE_STRATEGY+".option.used",
                        name="used"),
                @PropertyOption(
                        value='%'+SiteConfiguration.CACHE_STRATEGY+".option.all",
                        name="all")
            }, value="none"),
        @Property(name=SiteConfiguration.CACHE_ID),
        @Property(name=SiteConfiguration.SITE_FIELD_MAPPINGS,cardinality=1000, //positive number to use an Array
            value={
                "dbp-ont:*",
                "dbp-ont:thumbnail | d=xsd:anyURI > foaf:depiction",
                "dbp-prop:latitude | d=xsd:decimal > geo:lat",
                "dbp-prop:longitude | d=xsd:decimal > geo:long",
                "dbp-prop:population | d=xsd:integer",
                "dbp-prop:website | d=xsd:anyURI > foaf:homepage"
            })
        })
public class ReferencedSiteImpl implements ReferencedSite {
    static final int maxInt = Integer.MAX_VALUE;
    private final Logger log;
    private ComponentContext context;
    private FieldMapper fieldMappings;
    /**
     * Used to create Sign instances
     */
    private DefaultSignFactory signFactory = DefaultSignFactory.getInstance();

    private final Object searcherAndDereferencerLock = new Object();
    private Boolean dereferencerEqualsEntitySearcherComponent;
    private ComponentFactoryListener dereferencerComponentFactoryListener;
    private ComponentFactoryListener searcherComponentFactoryListener;

//    private String dereferencerComponentName;
    private ComponentInstance dereferencerComponentInstance;
    private EntityDereferencer dereferencer;

//    private String entitySearcherComponentName;
    private EntitySearcher entitySearcher;
    private ComponentInstance entitySearcherComponentInstance;

    private ServiceTracker cacheTracker;
    
    private SiteConfiguration siteConfiguration;
    
    /**
     * The {@link OfflineMode} is used by Stanbol to indicate that no external
     * service should be referenced. For the ReferencedSiteImpl this means that
     * the {@link EntityDereferencer} and {@link EntitySearcher} interfaces
     * are no longer used.<p>
     * @see #enableOfflineMode(OfflineMode)
     * @see #disableOfflineMode(OfflineMode)
     * @see #isOfflineMode()
     * @see #ensureOnline(String, Class)
     */
    @Reference(
        cardinality=ReferenceCardinality.OPTIONAL_UNARY,
        policy=ReferencePolicy.DYNAMIC,
        bind="enableOfflineMode",
        unbind="disableOfflineMode",
        strategy=ReferenceStrategy.EVENT)
    private OfflineMode offlineMode;


    
    public ReferencedSiteImpl(){
        this(LoggerFactory.getLogger(ReferencedSiteImpl.class));
    }
    protected ReferencedSiteImpl(Logger log){
        this.log = log;
           log.info("create instance of "+this.getClass().getName());
    }
    public String getId(){
        return siteConfiguration.getId();
    }
    @Override
    public QueryResultList<Sign> findSigns(FieldQuery query) throws ReferencedSiteException {
        List<Sign> results;
        if(siteConfiguration.getCacheStrategy() == CacheStrategy.all){
            //TODO: check if query can be executed based on the base configuration of the Cache
            Cache cache = getCache();
            if(cache != null){
                try {
                    //When using the Cache, directly get the representations!
                    QueryResultList<Representation> representations = cache.findRepresentation((query));
                    results = new ArrayList<Sign>(representations.size());
                    for(Representation result : representations){
                        results.add(signFactory.getSign(getId(),result));
                    }
                    return new QueryResultListImpl<Sign>(query, results, Sign.class);
                } catch (YardException e) {
                    if(siteConfiguration.getEntitySearcherType()==null || isOfflineMode()){
                        throw new ReferencedSiteException("Unable to execute query on Cache "+siteConfiguration.getCacheId(),e);
                    } else {
                        log.warn(String.format("Error while performing query on Cache %s! Try to use remote site %s as fallback!",
                            siteConfiguration.getCacheId(),siteConfiguration.getQueryUri()),e);
                    }
                }
            } else {
                if(siteConfiguration.getEntitySearcherType()==null || isOfflineMode()){
                    throw new ReferencedSiteException(String.format("Unable to execute query on Cache %s because it is currently not active",
                        siteConfiguration.getCacheId()));
                } else {
                    log.warn(String.format("Cache %s currently not active will query remote Site %s as fallback",
                        siteConfiguration.getCacheId(),siteConfiguration.getQueryUri()));
                }
            }
        }
        QueryResultList<String> entityIds;
        if(entitySearcher == null) {
            throw new ReferencedSiteException(
                String.format("EntitySearcher %s not available for remote site %s!",siteConfiguration.getEntitySearcherType(),
                    siteConfiguration.getQueryUri()));
        }
        ensureOnline(siteConfiguration.getQueryUri(),entitySearcher.getClass());
        try {
            entityIds = entitySearcher.findEntities(query);
        } catch (IOException e) {
            throw new ReferencedSiteException(String.format("Unable to execute query on remote site %s with entitySearcher %s!",
                    siteConfiguration.getQueryUri(),siteConfiguration.getEntitySearcherType()), e);
        }
        int numResults = entityIds.size();
        List<Sign> entities = new ArrayList<Sign>(numResults);
        int errors = 0;
        ReferencedSiteException lastError = null;
        for(String id : entityIds){
            Sign entity;
            try {
                entity = getSign(id);
                if(entity == null){
                    log.warn("Unable to create Entity for ID that was selected by an FieldQuery (id="+id+")");
                }
                entities.add(entity);
                //use the position in the list as resultSocre
                entity.getRepresentation().set(RdfResourceEnum.resultScore.getUri(), Float.valueOf((float)numResults));
            } catch (ReferencedSiteException e) {
                lastError = e;
                errors++;
                log.warn(String.format("Unable to get Representation for Entity %s. -> %d Error%s for %d Entities in QueryResult (Reason:%s)",
                        id,errors,errors>1?"s":"",entityIds.size(),e.getMessage()));
            }
            //decrease numResults because it is used as resultScore for entities
            numResults--;
        }
        if(lastError != null){
            if(entities.isEmpty()){
                throw new ReferencedSiteException("Unable to get anly Representations for Entities selected by the parsed Query (Root-Cause is the last Exception trown)",lastError);
            } else {
                log.warn(String.format("Unable to get %d/%d Represetnations for selected Entities.",errors,entityIds.size()));
                log.warn("Stack trace of the last Exception:",lastError);
            }
        }
        return new QueryResultListImpl<Sign>(query, entities,Sign.class);
    }
    @Override
    public QueryResultList<Representation> find(FieldQuery query) throws ReferencedSiteException{
        if(siteConfiguration.getCacheStrategy() == CacheStrategy.all){
            //TODO: check if query can be executed based on the base configuration of the Cache
            Cache cache = getCache();
            if(cache != null){
                try {
                    return cache.find(query);
                } catch (YardException e) {
                    if(siteConfiguration.getEntitySearcherType()==null || isOfflineMode()){
                        throw new ReferencedSiteException("Unable to execute query on Cache "+siteConfiguration.getCacheId(),e);
                    } else {
                        log.warn(String.format("Error while performing query on Cache %s! Try to use remote site %s as fallback!",
                            siteConfiguration.getCacheId(),siteConfiguration.getQueryUri()),e);
                    }
                }
            } else {
                if(siteConfiguration.getEntitySearcherType()==null || isOfflineMode()){
                    throw new ReferencedSiteException(String.format("Unable to execute query because Cache %s is currently not active",
                        siteConfiguration.getCacheId()));
                } else {
                    log.warn(String.format("Cache %s currently not active will query remote Site %s as fallback",
                        siteConfiguration.getCacheId(),siteConfiguration.getQueryUri()));
                }
            }
        }
        if(entitySearcher == null){
            throw new ReferencedSiteException(
                String.format("EntitySearcher %s not available for remote site %s!",siteConfiguration.getEntitySearcherType(),
                    siteConfiguration.getQueryUri()));
        }
        ensureOnline(siteConfiguration.getQueryUri(), entitySearcher.getClass());
        try {
            return entitySearcher.find(query);
        } catch (IOException e) {
            throw new ReferencedSiteException("Unable execute Query on remote site "+
                siteConfiguration.getQueryUri(),e);
        }
    }
    @Override
    public QueryResultList<String> findReferences(FieldQuery query) throws ReferencedSiteException {
        if(siteConfiguration.getCacheStrategy() == CacheStrategy.all){
            //TODO: check if query can be executed based on the base configuration of the Cache
            Cache cache = getCache();
            if(cache != null){
                try {
                    return cache.findReferences(query);
                } catch (YardException e) {
                    if(siteConfiguration.getEntitySearcherType()==null || isOfflineMode()){
                        throw new ReferencedSiteException("Unable to execute query on Cache "+siteConfiguration.getCacheId(),e);
                    } else {
                        log.warn(String.format("Error while performing query on Cache %s! Try to use remote site %s as fallback!",
                            siteConfiguration.getCacheId(),siteConfiguration.getQueryUri()),e);
                    }
                }
            } else {
                if(siteConfiguration.getEntitySearcherType()==null  || isOfflineMode()){
                    throw new ReferencedSiteException(
                        String.format("Unable to execute query on Cache %s because it is currently not active",
                            siteConfiguration.getCacheId()));
                } else {
                    log.warn(String.format("Cache %s currently not active will query remote Site %s as fallback",
                        siteConfiguration.getCacheId(),siteConfiguration.getQueryUri()));
                }
            }
        }
        if(entitySearcher == null){
            throw new ReferencedSiteException(
                String.format("EntitySearcher %s not available for remote site %s!",siteConfiguration.getEntitySearcherType(),
                    siteConfiguration.getQueryUri()));
        }
        ensureOnline(siteConfiguration.getQueryUri(), entitySearcher.getClass());
        try {
            return entitySearcher.findEntities(query);
        } catch (IOException e) {
            throw new ReferencedSiteException("Unable execute Query on remote site "+
                siteConfiguration.getQueryUri(),e);
        }
    }
    @Override
    public InputStream getContent(String id, String contentType) throws ReferencedSiteException {
        if(siteConfiguration.getEntityDereferencerType() == null){
            throw new ReferencedSiteException(
                String.format("Unable to get Content for Entity %s because No dereferencer configured for ReferencedSite %s",
                    id,getId()));
        }
        if(dereferencer == null){
            throw new ReferencedSiteException(
                String.format("Dereferencer %s for remote site %s is not available",siteConfiguration.getEntityDereferencerType(),
                siteConfiguration.getAccessUri()));
        }
        ensureOnline(siteConfiguration.getAccessUri(), dereferencer.getClass());
        try {
            return dereferencer.dereference(id, contentType);
        } catch (IOException e) {
            throw new ReferencedSiteException(
                String.format("Unable to load content for Entity %s and mediaType %s from remote site %s by using dereferencer %s",
                    id,contentType,siteConfiguration.getAccessUri(),siteConfiguration.getEntityDereferencerType()),e);
        }
    }
    @Override
    public Sign getSign(String id) throws ReferencedSiteException {
        Cache cache = getCache();
        Representation rep = null;
        long start = System.currentTimeMillis();
        if (cache != null) {
            try {
                rep = cache.getRepresentation(id);
            } catch (YardException e) {
                if (siteConfiguration.getEntityDereferencerType() == null || isOfflineMode()) {
                    throw new ReferencedSiteException(String.format("Unable to get Represetnation %s form Cache %s",
                        id, siteConfiguration.getCacheId()), e);
                } else {
                    log.warn(String.format("Unable to get Represetnation %s form Cache %s. Will dereference from remote site %s",
                            id, siteConfiguration.getCacheId(), siteConfiguration.getAccessUri()), e);
                }
            }
        } else {
            if (siteConfiguration.getEntityDereferencerType() == null || isOfflineMode()) {
                throw new ReferencedSiteException(String.format("Unable to get Represetnation %s because configured Cache %s is currently not available",
                        id, siteConfiguration.getCacheId()));
            } else {
                log.warn(String.format("Cache %s is currently not available. Will use remote site %s to load Representation %s",
                        siteConfiguration.getCacheId(), siteConfiguration.getEntityDereferencerType(), id));
            }
        }
        if (rep == null) { // no cache or not found in cache
            if(dereferencer == null){
                throw new ReferencedSiteException(String.format("Entity Dereferencer %s for accessing remote site %s is not available",
                    siteConfiguration.getEntityDereferencerType(),siteConfiguration.getAccessUri()));
            }
            ensureOnline(siteConfiguration.getAccessUri(), dereferencer.getClass());
            try {
                rep = dereferencer.dereference(id);
            } catch (IOException e) {
                throw new ReferencedSiteException(
                    String.format("Unable to load Representation for entity %s form remote site %s with dereferencer %s",
                        id, siteConfiguration.getAccessUri(), siteConfiguration.getEntityDereferencerType()), e);
            }
            //representation loaded from remote site and cache is available
            if (rep != null && cache != null) {// -> cache the representation
                try {
                    start = System.currentTimeMillis();
                    // reassigning the Representation here will remove all
                    // values not stored in the cache.
                    // TODO: I am not sure if that is a good or bad thing to do.
                    rep = cache.store(rep);
                    log.info(String.format("  - cached Representation %s in %d ms",    id, (System.currentTimeMillis() - start)));
                } catch (YardException e) {
                    log.warn(String.format("Unable to cache Represetnation %s in Cache %s! Representation not cached!",
                        id, siteConfiguration.getCacheId()), e);
                }
            }
        } else {
            log.info(String.format("  - loaded Representation %s from Cache in %d ms",
                    id, (System.currentTimeMillis() - start)));
        }
        return rep != null ? signFactory.getSign(getId(),rep) : null;
    }
    @Override
    public SiteConfiguration getConfiguration() {
        return siteConfiguration;
    }

    @Override
    public String toString() {
        return siteConfiguration!= null?siteConfiguration.getName():null;
    }
    @Override
    public int hashCode() {
        return siteConfiguration!=null?getId().hashCode():-1;
    }
    @Override
    public boolean equals(Object obj) {
        if(obj instanceof ReferencedSite) {
            SiteConfiguration osc = ((ReferencedSite)obj).getConfiguration();
            //this will return false if one of the two sites is not activated
            // -> this should be OK
            return siteConfiguration != null && osc != null &&
                getId().equals(osc.getId());
        } else {
            return false;
        }
    }
    @Override
    public FieldMapper getFieldMapper() {
        return fieldMappings;
    }

    /**
     * In case {@link CacheStrategy#all} this Method returns the
     * query factory of the Cache.
     * Otherwise it returns {@link DefaultQueryFactory#getInstance()}.
     */
    @Override
    public FieldQueryFactory getQueryFactory() {
        FieldQueryFactory factory = null;
        if(siteConfiguration.getCacheStrategy() == CacheStrategy.all){
            Cache cache = getCache();
            if(cache != null){
                factory = cache.getQueryFactory();
            }
        }
        if(factory == null){
            factory = DefaultQueryFactory.getInstance();
        }
        return factory;
    }
    public boolean supportsLocalMode(){
        return siteConfiguration.getCacheStrategy() == CacheStrategy.all &&
            getCache() != null;
    }
    public boolean supportsSearch(){
        return supportsLocalMode() ||
            entitySearcher != null;
    }
    /**
     * Internally used to get the Cache for this site. If
     * {@link CacheStrategy#none}, this methods always returns <code>null</code>,
     * otherwise it returns the Cache for the configured Yard or <code>null</code>
     * if no such Cache is available.
     * @return the cache or <code>null</code> if {@link CacheStrategy#none} or
     * the configured cache instance is not available.
     */
    protected Cache getCache(){
        return siteConfiguration.getCacheStrategy() == CacheStrategy.none?null:(Cache)cacheTracker.getService();
    }

    /*--------------------------------------------------------------------------
     *  OSGI LIFECYCLE and LISTENER METHODS
     *--------------------------------------------------------------------------
     */

    @SuppressWarnings("unchecked")
    @Activate
    protected void activate(final ComponentContext context) throws ConfigurationException, YardException, InvalidSyntaxException {
        log.debug("in {} activate with properties {}",
            ReferencedSiteImpl.class.getSimpleName(),context.getProperties());
        if(context == null || context.getProperties() == null){
            throw new IllegalStateException("No Component Context and/or Dictionary properties object parsed to the acticate methode");
        }
        this.context = context;
        //create the SiteConfiguration based on the parsed properties
        Map<String,Object> config = new HashMap<String,Object>();
        Dictionary<String,Object> properties = (Dictionary<String,Object>)context.getProperties();
        //copy the properties to a map
        for(Enumeration<String> e = properties.keys();e.hasMoreElements();){
            String key = e.nextElement();
            config.put(key, properties.get(key));
        }
        //NOTE that the constructor also validation of the parsed configuration
        siteConfiguration = new DefaultSiteConfiguration(config);
        log.info(" > initialise Referenced Site {}",siteConfiguration.getName());
        //if the accessUri is the same as the queryUri and both the dereferencer and
        //the entitySearcher uses the same component, than we need only one component
        //for both dependencies.
        this.dereferencerEqualsEntitySearcherComponent =
            //(1) accessURI == queryURI
            siteConfiguration.getAccessUri() != null && 
            siteConfiguration.getAccessUri().equals(siteConfiguration.getQueryUri()) &&
            //(2) entity dereferencer == entity searcher
            siteConfiguration.getEntityDereferencerType()!= null &&
            siteConfiguration.getEntityDereferencerType().equals(siteConfiguration.getEntitySearcherType());

        //init the fieldMapper based on the configuration
        fieldMappings = new DefaultFieldMapperImpl(ValueConverterFactory.getDefaultInstance());
        if(siteConfiguration.getFieldMappings() != null){
            log.debug(" > Initialise configured field mappings");
            for(String configuredMapping : siteConfiguration.getFieldMappings()){
                FieldMapping mapping = FieldMappingUtils.parseFieldMapping(configuredMapping);
                if(mapping != null){
                    log.debug("   - add FieldMapping {}",mapping);
                    fieldMappings.addMapping(mapping);
                }
            }
        }
        //now init the referenced Services
        initDereferencerAndEntitySearcher();

        // If a cache is configured init the ServiceTracker used to manage the
        // Reference to the cache!
        if(siteConfiguration.getCacheId() != null){
            String cacheFilter = String.format("(&(%s=%s)(%s=%s))",
                    Constants.OBJECTCLASS,Cache.class.getName(),
                    Cache.CACHE_YARD,siteConfiguration.getCacheId());
            cacheTracker = new ServiceTracker(context.getBundleContext(),
                    context.getBundleContext().createFilter(cacheFilter), null);
            cacheTracker.open();
        }
    }

    /**
     * Initialise the dereferencer and searcher component as soon as the according
     * {@link ComponentFactory} gets registered.<p>
     * First this Methods tries to find the according {@link ServiceReference}s
     * directly. If they are not available (e.g. because the component factories
     * are not yet started) than it adds a {@link ServiceListener} for the missing
     * {@link ComponentFactory} that calls the {@link #createDereferencerComponent(ComponentFactory)}
     * and {@link #createEntitySearcherComponent(ComponentFactory)} as soon as
     * the factory gets registered.
     * @throws InvalidSyntaxException if the #entitySearcherComponentName or the
     * {@link #dereferencerComponentName} somehow cause an invalid formated string
     * that can not be used to parse a {@link Filter}.
     */
    private void initDereferencerAndEntitySearcher() throws InvalidSyntaxException {
        if(siteConfiguration.getEntitySearcherType() != null) {
            String componentNameFilterString = String.format("(%s=%s)",
                    "component.name",siteConfiguration.getEntitySearcherType());
            String filterString = String.format("(&(%s=%s)%s)",
                    Constants.OBJECTCLASS,ComponentFactory.class.getName(),
                    componentNameFilterString);
            ServiceReference[] refs = context.getBundleContext().getServiceReferences(ComponentFactory.class.getName(),componentNameFilterString);
            if(refs != null && refs.length>0){
                createEntitySearcherComponent((ComponentFactory)context.getBundleContext().getService(refs[0]));
            } else { //service factory not yet available -> add servicelistener
                this.searcherComponentFactoryListener = new ComponentFactoryListener(context.getBundleContext());
                context.getBundleContext().addServiceListener(this.searcherComponentFactoryListener,filterString); //NOTE: here the filter MUST include also the objectClass!
            }
            //context.getComponentInstance().dispose();
            //throw an exception to avoid an successful activation
        }
        if(siteConfiguration.getEntityDereferencerType() != null && !this.dereferencerEqualsEntitySearcherComponent){
            String componentNameFilterString = String.format("(%s=%s)",
                    "component.name",siteConfiguration.getEntityDereferencerType());
            String filterString = String.format("(&(%s=%s)%s)",
                    Constants.OBJECTCLASS,ComponentFactory.class.getName(),
                    componentNameFilterString);
            ServiceReference[] refs = context.getBundleContext().getServiceReferences(ComponentFactory.class.getName(),componentNameFilterString);
            if(refs != null && refs.length>0){
                createDereferencerComponent((ComponentFactory)context.getBundleContext().getService(refs[0]));
            } else { //service factory not yet available -> add servicelistener
                this.dereferencerComponentFactoryListener = new ComponentFactoryListener(context.getBundleContext());
                this.context.getBundleContext().addServiceListener(this.dereferencerComponentFactoryListener,filterString); //NOTE: here the filter MUST include also the objectClass!
            }
        }
    }
    /**
     * Creates the entity searcher component used by this {@link ReferencedSite}
     * (and configured via the {@link SiteConfiguration#ENTITY_SEARCHER_TYPE} property).<p>
     * If the {@link SiteConfiguration#ENTITY_DEREFERENCER_TYPE} is set to the same vale
     * and the {@link #accessUri} also equals the {@link #queryUri}, than the
     * component created for the entity searcher is also used as dereferencer.
     * @param factory The component factory used to create the
     * {@link #entitySearcherComponentInstance}
     */
    @SuppressWarnings("unchecked")
    protected void createEntitySearcherComponent(ComponentFactory factory){
        //both create*** methods sync on the searcherAndDereferencerLock to avoid
        //multiple component instances because of concurrent calls
        synchronized (this.searcherAndDereferencerLock ) {
            if(entitySearcherComponentInstance == null){
                this.entitySearcherComponentInstance = factory.newInstance(OsgiUtils.copyConfig(context.getProperties()));
                this.entitySearcher = (EntitySearcher)entitySearcherComponentInstance.getInstance();
            }
            if(dereferencerEqualsEntitySearcherComponent){
                this.dereferencer = (EntityDereferencer) entitySearcher;
            }
        }
    }
    /**
     * Creates the entity dereferencer component used by this {@link ReferencedSite}.
     * The implementation used as the dereferencer is configured by the
     * {@link SiteConfiguration#ENTITY_DEREFERENCER_TYPE} property.
     * @param factory the component factory used to create the {@link #dereferencer}
     */
    @SuppressWarnings("unchecked")
    protected void createDereferencerComponent(ComponentFactory factory){
        //both create*** methods sync on searcherAndDereferencerLock to avoid
        //multiple component instances because of concurrent calls
        synchronized (this.searcherAndDereferencerLock) {
            if(dereferencerComponentInstance == null){
                dereferencerComponentInstance=factory.newInstance(OsgiUtils.copyConfig(context.getProperties()));
                this.dereferencer = (EntityDereferencer)dereferencerComponentInstance.getInstance();
            }
        }
    }

    /**
     * Simple {@link ServiceListener} implementation that is used to get notified
     * if one of the {@link ComponentFactory component factories} for the
     * configured implementation of the {@link EntityDereferencer} or
     * {@link EntitySearcher} interfaces get registered.
     * @author Rupert Westenthaler
     *
     */
    private class ComponentFactoryListener implements ServiceListener {
        private BundleContext bundleContext;
        protected ComponentFactoryListener(BundleContext bundleContext){
            if(bundleContext == null){
                throw new IllegalArgumentException("The BundleContext MUST NOT be NULL!");
            }
            this.bundleContext = bundleContext;
        }
        @Override
        public void serviceChanged(ServiceEvent event) {
            Object eventComponentName = event.getServiceReference().getProperty("component.name");
            if(event.getType() == ServiceEvent.REGISTERED){
                log.info(String.format("Process ServceEvent for ComponentFactory %s and State REGISTERED",
                        eventComponentName));
                ComponentFactory factory = (ComponentFactory)bundleContext.getService(event.getServiceReference());
                if(siteConfiguration.getEntityDereferencerType() != null &&
                        siteConfiguration.getEntityDereferencerType().equals(eventComponentName)){
                    createDereferencerComponent(factory);
                }
                if(siteConfiguration.getEntitySearcherType()!= null &&
                        siteConfiguration.getEntitySearcherType().equals(eventComponentName)){
                    createEntitySearcherComponent(factory);
                }
            } else {
                log.info(String.format("Ignore ServiceEvent for ComponentFactory %s and state %s",
                        eventComponentName,
                        event.getType()==ServiceEvent.MODIFIED?"MODIFIED":event.getType()==ServiceEvent.UNREGISTERING?"UNREGISTERING":"MODIFIED_ENDMATCH"));
            }
        }

    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        log.info("deactivate Referenced Site {}",siteConfiguration.getName());
        this.dereferencer = null;
        if(this.dereferencerComponentInstance != null){
            this.dereferencerComponentInstance.dispose();
            this.dereferencerComponentInstance = null;
        }
        this.entitySearcher = null;
        if(this.entitySearcherComponentInstance != null){
            this.entitySearcherComponentInstance.dispose();
            this.entitySearcherComponentInstance = null;
        }
        if(searcherComponentFactoryListener != null){
            context.getBundleContext().removeServiceListener(searcherComponentFactoryListener);
            searcherComponentFactoryListener = null;
        }
        if(dereferencerComponentFactoryListener != null){
            context.getBundleContext().removeServiceListener(dereferencerComponentFactoryListener);
            dereferencerComponentFactoryListener = null;
        }
        if(cacheTracker != null){
            cacheTracker.close();
            cacheTracker = null;
        }
        this.fieldMappings = null;
        this.context = null;
        this.siteConfiguration = null;
    }
    
    /* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
     * Method for handling the OfflineMode
     * - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
     */
    
    /**
     * Called by the ConfigurationAdmin to bind the {@link #offlineMode} if the
     * service becomes available
     * @param mode 
     */
    protected final void enableOfflineMode(OfflineMode mode){
        this.offlineMode = mode;
    }
    /**
     * Called by the ConfigurationAdmin to unbind the {@link #offlineMode} if the
     * service becomes unavailable
     * @param mode
     */
    protected final void disableOfflineMode(OfflineMode mode){
        this.offlineMode = null;
    }
    /**
     * Returns <code>true</code> only if Stanbol operates in {@link OfflineMode}.
     * @return the offline state
     */
    protected final boolean isOfflineMode(){
        return offlineMode != null;
    }
    /**
     * Basically this Method throws an {@link ReferencedSiteException} in case
     * Stanbol operates in offline mode
     * @param uri the URI of the remote service
     * @param clazz the clazz of the service that would like to refer the remote
     * service
     * @throws ReferencedSiteException in case {@link #isOfflineMode()} returns
     * <code>true</code>
     */
    private void ensureOnline(String uri, Class<?> clazz) throws ReferencedSiteException {
        if(isOfflineMode()){
            throw new ReferencedSiteException(String.format(
                "Unable to access remote Service %s by using %s because Stanbol runs in OfflineMode",
                uri,clazz.getSimpleName()));
        }
    }
}
