package com.armedia.acm.plugins.casefile.dao;

import com.armedia.acm.core.exceptions.AcmObjectNotFoundException;
import com.armedia.acm.data.AcmAbstractDao;
import com.armedia.acm.plugins.casefile.model.CaseByStatusDto;
import com.armedia.acm.plugins.casefile.model.CaseFile;
import com.armedia.acm.plugins.casefile.model.CaseFileConstants;
import com.armedia.acm.plugins.casefile.model.TimePeriod;

import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import com.armedia.acm.services.participants.model.ParticipantTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by armdev on 8/26/14.
 */
public class CaseFileDao extends AcmAbstractDao<CaseFile>
{
    private Logger LOG = LoggerFactory.getLogger(getClass());

    @Override
    protected Class<CaseFile> getPersistenceClass()
    {
        return CaseFile.class;
    }

    public List<CaseFile> findCaseFiles()
    {
        CriteriaBuilder builder = getEm().getCriteriaBuilder();
        CriteriaQuery<CaseFile> query = builder.createQuery(CaseFile.class);
        Root<CaseFile> cfRoot = query.from(CaseFile.class);
        query.select(cfRoot);


        // TODO: parameterized order by
        query.orderBy(builder.desc(cfRoot.get("created")));

        TypedQuery<CaseFile> dbQuery = getEm().createQuery(query);

        List<CaseFile> results = dbQuery.getResultList();

        return results;

    }

    public List<CaseByStatusDto> getAllCasesByStatus() {
        String queryText = "SELECT cf.status, COUNT(cf) as counted FROM CaseFile cf GROUP BY cf.status";
        Query caseGroupedByStatus = getEm().createQuery(queryText);

        List<Object[]> caseGroupedByS = caseGroupedByStatus.getResultList();


        List<CaseByStatusDto> result = new ArrayList<CaseByStatusDto>();

        for(Object[] caseStatus : caseGroupedByS){
            CaseByStatusDto caseByS = new CaseByStatusDto();
            caseByS.setStatus((String) caseStatus[0]);
            caseByS.setCount(((Number)caseStatus[1]).intValue());
            result.add(caseByS);
        }
        return result;
    }

    public List<CaseFile> getCaseFilesByUser(String user) throws AcmObjectNotFoundException{
        String queryText = "SELECT cf FROM CaseFile cf " +
                "WHERE cf.creator = :user";
        Query casesByUser = getEm().createQuery(queryText);
        casesByUser.setParameter("user",user);
        List<CaseFile> retval = casesByUser.getResultList();
        if(retval.isEmpty()) {
            throw new AcmObjectNotFoundException("Case File",null, "Cases not found for the user: "+user+"",null);
        }
        return retval;
    }
    public List<CaseFile> getNotClosedCaseFilesByUser(String user) throws AcmObjectNotFoundException{
        String queryText =
                "SELECT cf " +
                        "FROM CaseFile cf, " +
                        "     AcmParticipant ap " +
                        "WHERE " +
                        "     cf.id = ap.objectId " +
                        "AND  ap.objectType = '" + CaseFileConstants.OBJECT_TYPE + "' " +
                        "AND  ap.participantType = '" + ParticipantTypes.ASSIGNEE + "' " +
                        "AND  ap.participantLdapId = :user " +
                        "AND  cf.status <> :statusName " +
                        "ORDER BY " +
                        "     cf.dueDate ASC";
        Query casesByUser = getEm().createQuery(queryText);
        casesByUser.setParameter("user",user);
        casesByUser.setParameter("statusName","CLOSED");
        List<CaseFile> retval = casesByUser.getResultList();
        if(retval.isEmpty()) {
            throw new AcmObjectNotFoundException("Case File",null, "Cases not found for the user: "+user+"",null);
        }
        return retval;
    }
        public List<CaseByStatusDto> getCasesByStatusAndByTimePeriod(TimePeriod numberOfDaysFromToday) {
        String queryText = "SELECT cf.status, COUNT(cf) as counted FROM CaseFile cf WHERE cf.created >= :created GROUP BY cf.status";
        Query caseGroupedByStatus = getEm().createQuery(queryText);

        caseGroupedByStatus.setParameter("created",shiftDateFromToday(numberOfDaysFromToday.getNumOfDays()));

        List<Object[]> caseGroupedByS = caseGroupedByStatus.getResultList();

        List<CaseByStatusDto> result = new ArrayList<CaseByStatusDto>();

        for(Object[] caseStatus : caseGroupedByS){
            CaseByStatusDto caseByS = new CaseByStatusDto();
            caseByS.setStatus((String) caseStatus[0]);
            caseByS.setCount(((Number)caseStatus[1]).intValue());
            result.add(caseByS);
        }
        return result;
    }


    public CaseFile findByCaseNumber(String caseNumber)
    {
        String queryText = "SELECT cf FROM CaseFile cf WHERE cf.caseNumber = :caseNumber";

        Query findByCaseNumber = getEm().createQuery(queryText);
        findByCaseNumber.setParameter("caseNumber", caseNumber);

        return (CaseFile) findByCaseNumber.getSingleResult();
    }
    
    public List<CaseFile> findByCaseNumberKeyword(String expression) {
    	CriteriaBuilder builder = getEm().getCriteriaBuilder();
    	CriteriaQuery<CaseFile> query = builder.createQuery(CaseFile.class);
    	Root<CaseFile> cf = query.from(CaseFile.class);
    	
    	query.select(cf);
    	
    	query.where(
    			builder.and(
    					builder.like(
    							builder.lower(cf.<String>get("caseNumber")), "%" + expression.toLowerCase() + "%"
    					)
    			)
    	);
    	
    	query.orderBy(builder.asc(cf.get("caseNumber")));
    	
    	TypedQuery<CaseFile> dbQuery = getEm().createQuery(query);
    	List<CaseFile> results = dbQuery.getResultList();
    	
    	return results;
    }
    
    @Transactional
    public int updateComplaintStatus(Long caseId, String newStatus, String modifier, Date date)
    {
        Query updateStatusQuery = getEm().createQuery(
                "UPDATE CaseFile " +
                        "SET status = :newStatus, " +
                        "modified = :modified, " +
                        "modifier = :modifier " +
                        "WHERE caseId = :caseId");
        updateStatusQuery.setParameter("newStatus", newStatus);
        updateStatusQuery.setParameter("modified", date);
        updateStatusQuery.setParameter("modifier", modifier);
        updateStatusQuery.setParameter("caseId", caseId);

        return updateStatusQuery.executeUpdate();
    }
    
    private Date shiftDateFromToday(int daysFromToday){
        Date nextDate;
        Date today = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(today);
        cal.add(Calendar.DATE, -daysFromToday);
        nextDate = cal.getTime();
        return nextDate;
    }

    /**
     * The method will return a map with all queues and sum of the orders in each queue
     *
     * @return - map object
     */
    public Map<String, Long> getNumberOfActiveOrdersByQueue()
    {
        Map<String, Long> retval = new LinkedHashMap<>();

        String query = "SELECT " +
                            "queue.displayOrder, " +
                            "queue.name, " +
                            "(SELECT " +
                                "COUNT(order.status) " +
                            "FROM " +
                                "CaseFile AS order " +
                            "WHERE " +
                                "order.queue.name = queue.name) " +
                        "FROM " +
                            "AcmQueue AS queue " +
                        "GROUP BY queue.displayOrder, queue.name " +
                        "ORDER BY queue.displayOrder";

        TypedQuery<Object[]> select = getEm().createQuery(query, Object[].class);
        List<Object[]> result = select.getResultList();

        try
        {
            retval = getNumberOfActiveOrdersByQueueResult(result);
        }
        catch(Exception e)
        {
            LOG.error("Cannot convert database result to Map object.", e);
        }

        return retval;
    }

    /**
     * This method will convert the result taken from database in a LinkedHashMap - to keep ordering of the queues
     *
     * @param result - List of objects that database return after executing the query that is not related to any Object
     * @return - map object
     * @throws Exception
     */
    private Map<String, Long> getNumberOfActiveOrdersByQueueResult(List<Object[]> result) throws Exception
    {
        Map<String, Long> retval = new LinkedHashMap<>();

        if (result != null)
        {
            retval = result.stream().collect(Collectors.toMap(resultItem -> (String) resultItem[1], resultItem -> (Long) resultItem[2], (v1, v2) -> null, LinkedHashMap::new));
        }

        return retval;
    }

}
