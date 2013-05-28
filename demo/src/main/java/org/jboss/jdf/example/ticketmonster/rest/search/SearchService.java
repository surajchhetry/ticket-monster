package org.jboss.jdf.example.ticketmonster.rest.search;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.ejb.Stateless;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.New;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.lucene.search.Query;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.FullTextQuery;
import org.hibernate.search.jpa.Search;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.hibernate.search.query.dsl.Unit;
import org.jboss.jdf.example.ticketmonster.model.Event;
import org.jboss.jdf.example.ticketmonster.model.Show;
import org.jboss.jdf.example.ticketmonster.util.ForSearch;

@Stateless
@Path("/search")
public class SearchService {
    @Inject
    EntityManager em;
    @Inject
    Logger logger;

    // @Inject @ForSearch FullTextEntityManager ftem;

    private FullTextEntityManager ftem() {
        return Search.getFullTextEntityManager(em);
        // return ftem;
    }

    private void log(String message) {
        logger.info(message);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ShowResults search(@QueryParam("query") String searchString, 
        @QueryParam("latitude") Double latitude, @QueryParam("longitude") Double longitude) {
        log("Entering search");
        if (searchString == null || searchString.length() == 0) {
            log("search string is empty or null");
            throw new WebApplicationException(new RuntimeException("Query must have a QueryParam 'query'"),
                Response.Status.BAD_REQUEST);
        }
        log("search string is " + searchString);
        log("search latitude is " + latitude + " and longitude is " + longitude);
        QueryBuilder qb = ftem().getSearchFactory().buildQueryBuilder().forEntity(Show.class).get();
        Query luceneQuery;
        Query termsQuery = qb.keyword()
            .onField("event.name").boostedTo(10f)
            .andField("event.description")
            .andField("event.category.description").boostedTo(3f)
            .andField("venue.name").boostedTo(5f)
            .matching(searchString)
            .createQuery();
        if (latitude != null && longitude != null) {
            Query localQuery = qb.spatial()
                .onCoordinates("venue.address.coordinates")
                .within(50, Unit.KM)
                .ofLatitude(latitude).andLongitude(longitude)
                .createQuery();
            luceneQuery = qb.bool()
                .must(termsQuery)
                .must(localQuery)
                .createQuery();
        }
        else {
            luceneQuery = termsQuery;
        }
        log("Executing lucene query " + luceneQuery.toString());
        FullTextQuery objectQuery = ftem().createFullTextQuery(luceneQuery, Show.class);
        objectQuery.setResultTransformer(ShowViewResultTransformer.INSTANCE);
        return new ShowResults(objectQuery.getResultList());
    }
}