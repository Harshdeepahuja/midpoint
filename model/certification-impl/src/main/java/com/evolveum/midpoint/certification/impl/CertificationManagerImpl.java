/*
 * Copyright (c) 2010-2015 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.certification.impl;

import com.evolveum.midpoint.certification.api.CertificationManager;
import com.evolveum.midpoint.certification.impl.handlers.CertificationHandler;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.model.api.PolicyViolationException;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.match.MatchingRuleRegistry;
import com.evolveum.midpoint.prism.parser.XNodeSerializer;
import com.evolveum.midpoint.prism.path.IdItemPathSegment;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.path.NameItemPathSegment;
import com.evolveum.midpoint.prism.query.AndFilter;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.OrderDirection;
import com.evolveum.midpoint.prism.query.RefFilter;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.ObjectDeltaOperation;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.security.api.SecurityEnforcer;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCaseType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationDecisionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author mederly
 */
@Service
public class CertificationManagerImpl implements CertificationManager {

    private static final transient Trace LOGGER = TraceManager.getTrace(CertificationManager.class);

    public static final String INTERFACE_DOT = CertificationManager.class.getName() + ".";
    public static final String OPERATION_CREATE_CAMPAIGN = INTERFACE_DOT + "createCampaign";
    public static final String OPERATION_START_STAGE = INTERFACE_DOT + "startStage";
    public static final String OPERATION_RECORD_REVIEWER_DECISION = INTERFACE_DOT + "recordReviewerDecision";

    @Autowired
    private PrismContext prismContext;

    @Autowired
    private ModelService modelService;

    @Autowired
    protected SecurityEnforcer securityEnforcer;

    @Autowired
    private MatchingRuleRegistry matchingRuleRegistry;

    // TODO temporary hack because of some problems in model service...
    @Autowired
    @Qualifier("cacheRepositoryService")
    protected RepositoryService repositoryService;

    private PrismObjectDefinition<AccessCertificationCampaignType> campaignObjectDefinition = null;

    private Map<String,CertificationHandler> registeredHandlers = new HashMap<>();

    public void registerHandler(String typeUri, CertificationHandler handler) {
        if (registeredHandlers.containsKey(typeUri)) {
            throw new IllegalStateException("There is already a handler for certification type " + typeUri);
        }
        registeredHandlers.put(typeUri, handler);
    }

    public CertificationHandler findCertificationHandler(AccessCertificationDefinitionType accessCertificationDefinitionType) {
        if (StringUtils.isBlank(accessCertificationDefinitionType.getHandlerUri())) {
            throw new IllegalArgumentException("No handler URI for access certification definition " + accessCertificationDefinitionType);
        }
        CertificationHandler handler = registeredHandlers.get(accessCertificationDefinitionType.getHandlerUri());
        if (handler == null) {
            throw new IllegalStateException("No handler for certification definition " + accessCertificationDefinitionType.getHandlerUri());
        }
        return handler;
    }

    @Override
    public AccessCertificationCampaignType createCampaign(AccessCertificationDefinitionType certDefinition, AccessCertificationCampaignType campaign, Task task, OperationResult parentResult) throws SchemaException, SecurityViolationException, ConfigurationException, ObjectNotFoundException, CommunicationException, ExpressionEvaluationException, ObjectAlreadyExistsException, PolicyViolationException {
        Validate.notNull(certDefinition, "certificationDefinition");
        Validate.notNull(certDefinition.getOid(), "certificationDefinition.oid");
        if (campaign != null) {
            Validate.isTrue(campaign.getOid() == null, "Certification campaign with non-null OID is not permitted.");
        }

        OperationResult result = parentResult.createSubresult(OPERATION_CREATE_CAMPAIGN);
        try {
            AccessCertificationCampaignType newCampaign = createCampaignObject(certDefinition, campaign, task, result);
            addObject(newCampaign, task, result);
            return newCampaign;
        } finally {
            result.computeStatus();
        }
    }

    private AccessCertificationCampaignType createCampaignObject(AccessCertificationDefinitionType definition, AccessCertificationCampaignType campaign,
                                                                 Task task, OperationResult result) throws SecurityViolationException {
        AccessCertificationCampaignType newCampaign = new AccessCertificationCampaignType(prismContext);
        Date now = new Date();

        if (campaign != null && campaign.getName() != null) {
            campaign.setName(campaign.getName());
        } else {
            newCampaign.setName(new PolyStringType("Campaign for " + definition.getName().getOrig() + " started " + now));
        }

        if (campaign != null && campaign.getDescription() != null) {
            newCampaign.setDescription(newCampaign.getDescription());
        } else {
            newCampaign.setDescription(definition.getDescription());
        }

        if (campaign != null && campaign.getOwnerRef() != null) {
            newCampaign.setOwnerRef(campaign.getOwnerRef());
        } else if (definition.getOwnerRef() != null) {
            newCampaign.setOwnerRef(definition.getOwnerRef());
        } else {
            newCampaign.setOwnerRef(securityEnforcer.getPrincipal().toObjectReference());
        }

        if (campaign != null && campaign.getTenantRef() != null) {
            newCampaign.setTenantRef(campaign.getTenantRef());
        } else {
            newCampaign.setTenantRef(definition.getTenantRef());
        }

        newCampaign.setCurrentStageNumber(1);

        ObjectReferenceType typeRef = new ObjectReferenceType();
        typeRef.setType(AccessCertificationDefinitionType.COMPLEX_TYPE);
        typeRef.setOid(definition.getOid());
        newCampaign.setDefinitionRef(typeRef);

        return newCampaign;
    }

    @Override
    public void startStage(AccessCertificationCampaignType campaign, Task task, OperationResult parentResult) throws SchemaException, SecurityViolationException, ConfigurationException, ObjectNotFoundException, CommunicationException, ExpressionEvaluationException, ObjectAlreadyExistsException, PolicyViolationException {
        Validate.notNull(campaign, "campaign");
        Validate.notNull(campaign.getOid(), "campaign.oid");
        Validate.notNull(campaign.getDefinitionRef(), "campaign.definitionRef");

        OperationResult result = parentResult.createSubresult(OPERATION_START_STAGE);

        AccessCertificationDefinitionType certDefinition = resolveCertificationDef(campaign, task, result);

        try {
            CertificationHandler handler = findCertificationHandler(certDefinition);
            handler.startStage(certDefinition, campaign, task, result);
        } finally {
            result.computeStatus();
        }
    }

    private AccessCertificationDefinitionType resolveCertificationDef(AccessCertificationCampaignType campaign, Task task, OperationResult result) throws ConfigurationException, ObjectNotFoundException, SchemaException, CommunicationException, SecurityViolationException {
        PrismReferenceValue defRefValue = campaign.getDefinitionRef().asReferenceValue();
        if (defRefValue.getObject() != null) {
            return (AccessCertificationDefinitionType) defRefValue.getObject().asObjectable();
        }
        return modelService.getObject(AccessCertificationDefinitionType.class, defRefValue.getOid(), null, task, result).asObjectable();
    }

    private void addObject(ObjectType objectType, Task task, OperationResult result) throws CommunicationException, ObjectAlreadyExistsException, ExpressionEvaluationException, PolicyViolationException, SchemaException, SecurityViolationException, ConfigurationException, ObjectNotFoundException {
        ObjectDelta objectDelta = ObjectDelta.createAddDelta(objectType.asPrismObject());
        Collection<ObjectDeltaOperation<?>> ops = modelService.executeChanges((Collection) Arrays.asList(objectDelta), null, task, result);
        ObjectDeltaOperation odo = ops.iterator().next();
        objectType.setOid(odo.getObjectDelta().getOid());
    }

    @Override
    public List<AccessCertificationCaseType> searchCases(String campaignOid, ObjectQuery query,
                                                         Collection<SelectorOptions<GetOperationOptions>> options,
                                                         Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, SecurityViolationException, ConfigurationException, CommunicationException {
        // temporary implementation: simply fetches the whole campaign and selects requested items by itself
        ObjectFilter filter = query != null ? query.getFilter() : null;
        ObjectPaging paging = query != null ? query.getPaging() : null;
        AccessCertificationCampaignType campaign = getCampaign(campaignOid, options, task, parentResult);
        List<AccessCertificationCaseType> caseList = getCases(campaign, filter, task, parentResult);
        caseList = doSortingAndPaging(caseList, paging);
        return caseList;
    }

    @Override
    public List<AccessCertificationCaseType> searchDecisions(String campaignOid, String reviewerOid,
                                                             ObjectQuery query,
                                                             Collection<SelectorOptions<GetOperationOptions>> options,
                                                             Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, SecurityViolationException, ConfigurationException, CommunicationException {

        // enhance filter with reviewerRef
        ObjectFilter enhancedFilter;
        ObjectReferenceType reviewerRef = ObjectTypeUtil.createObjectRef(reviewerOid, ObjectTypes.USER);
        ObjectFilter reviewerFilter = RefFilter.createReferenceEqual(
                new ItemPath(AccessCertificationCaseType.F_REVIEWER_REF),
                AccessCertificationCaseType.class, prismContext, reviewerRef.asReferenceValue());
        if (query == null || query.getFilter() == null) {
            enhancedFilter = reviewerFilter;
        } else {
            enhancedFilter = AndFilter.createAnd(query.getFilter(), reviewerFilter);
        }

        // retrieve cases, filtered
        AccessCertificationCampaignType campaign = getCampaign(campaignOid, options, task, parentResult);
        List<AccessCertificationCaseType> caseList = getCases(campaign, enhancedFilter, task, parentResult);

        // sort and page cases
        ObjectPaging paging = query != null ? query.getPaging() : null;
        caseList = doSortingAndPaging(caseList, paging);

        // remove irrelevant decisions from each case
        int stage = getCurrentStageNumber(campaign);
        for (AccessCertificationCaseType _case : caseList) {
            Iterator<AccessCertificationDecisionType> decisionIterator = _case.getDecision().iterator();
            while (decisionIterator.hasNext()) {
                AccessCertificationDecisionType decision = decisionIterator.next();
                if (decision.getStageNumber() != stage || !decision.getReviewerRef().getOid().equals(reviewerOid)) {
                    decisionIterator.remove();
                }
            }
        }

        return caseList;
    }

    protected List<AccessCertificationCaseType> doSortingAndPaging(List<AccessCertificationCaseType> caseList, ObjectPaging paging) {
        if (paging == null) {
            return caseList;
        }

        // sorting
        if (paging.getOrderBy() != null) {
            Comparator<AccessCertificationCaseType> comparator = createComparator(paging.getOrderBy(), paging.getDirection());
            if (comparator != null) {
                caseList = new ArrayList<>(caseList);
                Collections.sort(caseList, comparator);
            }
        }
        // paging
        if (paging.getOffset() != null || paging.getMaxSize() != null) {
            int offset = paging.getOffset() != null ? paging.getOffset() : 0;
            int maxSize = paging.getMaxSize() != null ? paging.getMaxSize() : Integer.MAX_VALUE;
            if (offset >= caseList.size()) {
                caseList = new ArrayList<>();
            } else {
                if (maxSize > caseList.size() - offset) {
                    maxSize = caseList.size() - offset;
                }
                caseList = caseList.subList(offset, offset+maxSize);
            }
        }
        return caseList;
    }

    protected List<AccessCertificationCaseType> getCases(AccessCertificationCampaignType campaign, ObjectFilter filter, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, SecurityViolationException, CommunicationException, ConfigurationException {
        // temporary implementation: simply fetches the whole campaign and selects requested items by itself
        List<AccessCertificationCaseType> caseList = campaign.getCase();

        // filter items
        if (filter != null) {
            Iterator<AccessCertificationCaseType> caseIterator = caseList.iterator();
            while (caseIterator.hasNext()) {
                AccessCertificationCaseType _case = caseIterator.next();
                if (!ObjectQuery.match(_case, filter, matchingRuleRegistry)) {
                    caseIterator.remove();
                }
            }
        }

        return caseList;
    }

    private AccessCertificationCampaignType getCampaign(String campaignOid, Collection<SelectorOptions<GetOperationOptions>> options, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, SecurityViolationException, CommunicationException, ConfigurationException {
        return modelService.getObject(AccessCertificationCampaignType.class, campaignOid, options, task, parentResult).asObjectable();
    }


    /**
     * Experimental implementation: we support ordering by subject object name, target object name.
     * However, there are no QNames that exactly match these options. So we map the following QNames to ordering attributes:
     *
     * F_SUBJECT_REF -> ordering by subject name
     * F_TARGET_REF -> ordering by target name
     *
     * The requirement is that object names were fetched as well (resolveNames option)
     *
     */
    private Comparator<AccessCertificationCaseType> createComparator(QName orderBy, OrderDirection direction) {
        if (QNameUtil.match(orderBy, AccessCertificationCaseType.F_SUBJECT_REF)) {
            return createSubjectNameComparator(direction);
        } else if (QNameUtil.match(orderBy, AccessCertificationCaseType.F_TARGET_REF)) {
            return createTargetNameComparator(direction);
        } else {
            LOGGER.warn("Unsupported sorting attribute {}. Results will not be sorted.", orderBy);
            return null;
        }
    }

    private Comparator<AccessCertificationCaseType> createSubjectNameComparator(final OrderDirection direction) {
        return new Comparator<AccessCertificationCaseType>() {
            @Override
            public int compare(AccessCertificationCaseType o1, AccessCertificationCaseType o2) {
                return compareRefNames(o1.getSubjectRef(), o2.getSubjectRef(), direction);
            }
        };
    }

    private Comparator<AccessCertificationCaseType> createTargetNameComparator(final OrderDirection direction) {
        return new Comparator<AccessCertificationCaseType>() {
            @Override
            public int compare(AccessCertificationCaseType o1, AccessCertificationCaseType o2) {
                return compareRefNames(o1.getTargetRef(), o2.getTargetRef(), direction);
            }
        };
    }

    private int compareRefNames(ObjectReferenceType leftRef, ObjectReferenceType rightRef, OrderDirection direction) {
        if (leftRef == null) {
            return respectDirection(1, direction);      // null > anything
        } else if (rightRef == null) {
            return respectDirection(-1, direction);     // anything < null
        }

        // brutal hack - we (mis)use the fact that names are serialized as XNode comments
        String leftName = (String) leftRef.asReferenceValue().getUserData(XNodeSerializer.USER_DATA_KEY_COMMENT);
        String rightName = (String) rightRef.asReferenceValue().getUserData(XNodeSerializer.USER_DATA_KEY_COMMENT);
        if (leftName == null) {
            return respectDirection(1, direction);      // null > anything
        } else if (rightName == null) {
            return respectDirection(-1, direction);     // anything < null
        }

        int ordering = leftName.compareTo(rightName);
        return respectDirection(ordering, direction);
    }

    private int respectDirection(int ordering, OrderDirection direction) {
        if (direction == OrderDirection.ASCENDING) {
            return ordering;
        } else {
            return -ordering;
        }
    }

    @Override
    public void recordReviewerDecision(String campaignOid, long caseId, AccessCertificationDecisionType decision,
                                       Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, SecurityViolationException, ConfigurationException, CommunicationException, ObjectAlreadyExistsException {

        Validate.notNull(campaignOid, "campaignOid");
        Validate.notNull(decision, "decision");
        Validate.notNull(decision.getReviewerRef(), "decision.reviewerRef");
        Validate.notNull(decision.getReviewerRef().getOid(), "decision.reviewerRef.oid");

        OperationResult result = parentResult.createSubresult(OPERATION_RECORD_REVIEWER_DECISION);
        try {
            AccessCertificationCampaignType campaign = getCampaign(campaignOid, null, task, result);
            int currentStage = getCurrentStageNumber(campaign);
            if (decision.getStageNumber() != 0 && decision.getStageNumber() != currentStage) {
                throw new IllegalStateException("Cannot add decision with stage number (" + decision.getStageNumber() + ") other than current (" + currentStage + ")");
            }
            AccessCertificationCaseType _case = findCaseById(campaign, caseId);
            if (_case == null) {
                throw new ObjectNotFoundException("Case " + caseId + " was not found in campaign " + ObjectTypeUtil.toShortString(campaign));
            }
            Long existingDecisionId = null;
            for (AccessCertificationDecisionType d : _case.getDecision()) {
                if (d.getStageNumber() == currentStage &&
                        d.getReviewerRef().getOid().equals(decision.getReviewerRef().getOid())) {
                    existingDecisionId = d.asPrismContainerValue().getId();
                    break;
                }
            }
            AccessCertificationDecisionType decisionWithCorrectId = decision.clone();
            decisionWithCorrectId.asPrismContainerValue().setId(existingDecisionId);        // may be null if this is a new decision
            ItemPath decisionPath = new ItemPath(
                    new NameItemPathSegment(AccessCertificationCampaignType.F_CASE),
                    new IdItemPathSegment(caseId),
                    new NameItemPathSegment(AccessCertificationCaseType.F_DECISION));
            PropertyDelta decisionDelta =
                    PropertyDelta.createModificationReplaceProperty(decisionPath, getCampaignDefinition(), decisionWithCorrectId);

//            TODO: call model service instead of repository
//            ObjectDelta<AccessCertificationCampaignType> campaignDelta = ObjectDelta.createModifyDelta(
//                    campaignOid, decisionDelta, AccessCertificationCampaignType.class, prismContext);

            repositoryService.modifyObject(AccessCertificationCampaignType.class, campaignOid, Arrays.asList(decisionDelta), result);

        } finally {
            result.computeStatus();
        }
    }

    private PrismObjectDefinition<?> getCampaignDefinition() {
        if (campaignObjectDefinition != null) {
            return campaignObjectDefinition;
        }
        campaignObjectDefinition = prismContext.getSchemaRegistry().findObjectDefinitionByCompileTimeClass(AccessCertificationCampaignType.class);
        if (campaignObjectDefinition == null) {
            throw new IllegalStateException("Couldn't find definition for AccessCertificationCampaignType prism object");
        }
        return campaignObjectDefinition;
    }

    private int getCurrentStageNumber(AccessCertificationCampaignType campaign) {
        if (campaign.getCurrentStageNumber() == null) {
            return 1;
        } else {
            return campaign.getCurrentStageNumber();
        }
    }

    private AccessCertificationCaseType findCaseById(AccessCertificationCampaignType campaign, long caseId) {
        for (AccessCertificationCaseType _case : campaign.getCase()) {
            if (_case.asPrismContainerValue().getId() != null && _case.asPrismContainerValue().getId() == caseId) {
                return _case;
            }
        }
        return null;
    }
}
