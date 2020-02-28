/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.ibm.igc.repositoryconnector.mapping.classifications;

import org.odpi.egeria.connectors.ibm.igc.clientlibrary.IGCRestClient;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.IGCVersionEnum;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.model.base.Category;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.model.base.Term;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.model.common.ItemList;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.model.common.Reference;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.search.IGCSearchCondition;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.search.IGCSearchConditionSet;
import org.odpi.egeria.connectors.ibm.igc.repositoryconnector.IGCOMRSRepositoryConnector;
import org.odpi.egeria.connectors.ibm.igc.repositoryconnector.IGCRepositoryHelper;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.FunctionNotSupportedException;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.RepositoryErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Singleton defining the mapping to the OMRS "SubjectArea" classification.
 */
public class SubjectAreaMapper extends ClassificationMapping {

    private static final Logger log = LoggerFactory.getLogger(SubjectAreaMapper.class);

    private static class Singleton {
        private static final SubjectAreaMapper INSTANCE = new SubjectAreaMapper();
    }
    public static SubjectAreaMapper getInstance(IGCVersionEnum version) {
        return SubjectAreaMapper.Singleton.INSTANCE;
    }

    protected SubjectAreaMapper() {
        super(
                "category",
                "assigned_to_terms",
                "Referenceable",
                "SubjectArea"
        );
        addMappedOmrsProperty("name");
    }

    /**
     * Implements the SubjectArea classification for IGC 'category' assets. If a category comes
     * under any higher-level 'Subject Areas' category, such a category should be treated as subject area
     * and therefore will be mapped to a "SubjectArea" classification in OMRS.
     *
     * @param igcomrsRepositoryConnector connectivity to the IGC environment
     * @param classifications the list of classifications to which to add
     * @param fromIgcObject the IGC object for which the classification should exist
     * @param userId the user requesing the mapped classifications
     */
    @Override
    public void addMappedOMRSClassifications(IGCOMRSRepositoryConnector igcomrsRepositoryConnector,
                                             List<Classification> classifications,
                                             Reference fromIgcObject,
                                             String userId) {

        final String methodName = "addMappedOMRSClassifications";

        IGCRestClient igcRestClient = igcomrsRepositoryConnector.getIGCRestClient();

        // Retrieve all terms this category is assigned to
        if (fromIgcObject instanceof Category) {
            ItemList<Term> assignedToTerms = ((Category) fromIgcObject).getAssignedToTerms();

            // Only need to continue if there are any terms
            if (assignedToTerms != null) {
                List<Term> allAssignedToTerms = igcRestClient.getAllPages("assigned_to_terms", assignedToTerms);
                log.debug("Looking for SubjectArea mapping within {} candidate terms.", allAssignedToTerms.size());

                boolean isSubjectArea = false;

                // For each such relationship:
                for (Term termCandidate : allAssignedToTerms) {
                    // As soon as we find one that starts with Subject Area we can short-circuit out
                    if (termCandidate.getName().equals(getOmrsClassificationType())) {
                        isSubjectArea = true;
                        break;
                    }
                }

                if (isSubjectArea) {

                    log.debug(" ... found SubjectArea classification.");
                    InstanceProperties classificationProperties = igcomrsRepositoryConnector.getRepositoryHelper().addStringPropertyToInstance(
                            igcomrsRepositoryConnector.getRepositoryName(),
                            null,
                            "name",
                            fromIgcObject.getName(),
                            methodName
                    );
                    try {
                        Classification classification = getMappedClassification(
                                igcomrsRepositoryConnector,
                                classificationProperties,
                                fromIgcObject,
                                userId
                        );
                        classifications.add(classification);
                    } catch (RepositoryErrorException e) {
                        log.error("Unable to map classification.", e);
                    }

                }
            }
        }

    }

    /**
     * Search for SubjectArea by looking at ancestral categories of the term being under a "Subject Area" category.
     *
     * @param repositoryHelper the repository helper
     * @param repositoryName name of the repository
     * @param matchClassificationProperties the criteria to use when searching for the classification
     * @return IGCSearchConditionSet - the IGC search criteria to find entities based on this classification
     * @throws FunctionNotSupportedException when a regular expression is used for the search which is not supported
     */
    @Override
    public IGCSearchConditionSet getIGCSearchCriteria(OMRSRepositoryHelper repositoryHelper,
                                                      String repositoryName,
                                                      InstanceProperties matchClassificationProperties) throws FunctionNotSupportedException {

        final String methodName = "getIGCSearchCriteria";

        IGCSearchConditionSet igcSearchConditionSet = new IGCSearchConditionSet();

        IGCSearchCondition igcSearchCondition = new IGCSearchCondition(
                "assigned_to_terms.name",
                "=",
                getOmrsClassificationType()
        );
        IGCSearchConditionSet subjectArea = new IGCSearchConditionSet(igcSearchCondition);

        IGCSearchConditionSet byName = new IGCSearchConditionSet();
        // We can only search by name, so we will ignore all other properties
        if (matchClassificationProperties != null) {
            Map<String, InstancePropertyValue> properties = matchClassificationProperties.getInstanceProperties();
            if (properties.containsKey("name")) {
                PrimitivePropertyValue name = (PrimitivePropertyValue) properties.get("name");
                String subjectAreaName = name.valueAsString();
                IGCSearchCondition condition = IGCRepositoryHelper.getRegexSearchCondition(
                        repositoryHelper,
                        repositoryName,
                        methodName,
                        "name",
                        subjectAreaName
                );
                byName.addCondition(condition);
                byName.setMatchAnyCondition(false);
            }
        }
        if (byName.size() > 0) {
            igcSearchConditionSet.addNestedConditionSet(subjectArea);
            igcSearchConditionSet.addNestedConditionSet(byName);
            igcSearchConditionSet.setMatchAnyCondition(false);
            return igcSearchConditionSet;
        } else {
            return subjectArea;
        }

    }

}
