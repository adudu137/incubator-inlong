/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.manager.service.sort.util;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.inlong.manager.common.enums.FieldType;
import org.apache.inlong.manager.common.enums.TransformType;
import org.apache.inlong.manager.common.pojo.stream.StreamField;
import org.apache.inlong.manager.common.pojo.transform.TransformDefinition;
import org.apache.inlong.manager.common.pojo.transform.TransformResponse;
import org.apache.inlong.manager.common.pojo.transform.replacer.StringReplacerDefinition;
import org.apache.inlong.manager.common.pojo.transform.replacer.StringReplacerDefinition.ReplaceMode;
import org.apache.inlong.manager.common.pojo.transform.replacer.StringReplacerDefinition.ReplaceRule;
import org.apache.inlong.manager.common.pojo.transform.splitter.SplitterDefinition;
import org.apache.inlong.manager.common.pojo.transform.splitter.SplitterDefinition.SplitRule;
import org.apache.inlong.manager.common.util.Preconditions;
import org.apache.inlong.manager.common.util.StreamParseUtils;
import org.apache.inlong.sort.formats.common.FormatInfo;
import org.apache.inlong.sort.protocol.FieldInfo;
import org.apache.inlong.sort.protocol.transformation.CascadeFunction;
import org.apache.inlong.sort.protocol.transformation.ConstantParam;
import org.apache.inlong.sort.protocol.transformation.FieldRelationShip;
import org.apache.inlong.sort.protocol.transformation.StringConstantParam;
import org.apache.inlong.sort.protocol.transformation.function.CascadeFunctionWrapper;
import org.apache.inlong.sort.protocol.transformation.function.RegexpReplaceFirstFunction;
import org.apache.inlong.sort.protocol.transformation.function.RegexpReplaceFunction;
import org.apache.inlong.sort.protocol.transformation.function.SplitIndexFunction;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FieldRelationShipUtils {

    public static List<FieldRelationShip> createFieldRelationShips(TransformResponse transformResponse) {
        TransformType transformType = TransformType.forType(transformResponse.getTransformType());
        TransformDefinition transformDefinition = StreamParseUtils.parseTransformDefinition(
                transformResponse.getTransformDefinition(), transformType);
        List<StreamField> fieldList = transformResponse.getFieldList();
        String transformName = transformResponse.getTransformName();
        String preNodes = transformResponse.getPreNodeNames();
        switch (transformType) {
            case SPLITTER:
                SplitterDefinition splitterDefinition = (SplitterDefinition) transformDefinition;
                return createSplitterFieldRelationShips(fieldList, transformName, splitterDefinition, preNodes);
            case STRING_REPLACER:
                StringReplacerDefinition replacerDefinition = (StringReplacerDefinition) transformDefinition;
                return createReplacerFieldRelationShips(fieldList, transformName, replacerDefinition, preNodes);
            case DE_DUPLICATION:
            case FILTER:
                return createFieldRelationShips(fieldList, transformName);
            case JOINER:
                return createJoinerFieldRelationShips(fieldList, transformName);
            default:
                throw new UnsupportedOperationException(
                        String.format("Unsupported transformType=%s for Inlong", transformType));
        }
    }

    private static List<FieldRelationShip> createFieldRelationShips(List<StreamField> fieldList, String transformName) {
        return fieldList.stream()
                .map(streamFieldInfo -> FieldInfoUtils.parseStreamField(streamFieldInfo))
                .map(fieldInfo -> {
                    FieldInfo inputField = new FieldInfo(fieldInfo.getName(), fieldInfo.getNodeId(),
                            fieldInfo.getFormatInfo());
                    FieldInfo outputField = new FieldInfo(fieldInfo.getName(), transformName,
                            fieldInfo.getFormatInfo());
                    return new FieldRelationShip(inputField, outputField);
                }).collect(Collectors.toList());
    }

    private static List<FieldRelationShip> createJoinerFieldRelationShips(List<StreamField> fieldList,
            String transformName) {
        return fieldList.stream()
                .map(streamFieldInfo -> {
                    FormatInfo formatInfo = FieldInfoUtils.convertFieldFormat(
                            streamFieldInfo.getFieldType().name(),
                            streamFieldInfo.getFieldFormat());
                    FieldInfo inputField = new FieldInfo(streamFieldInfo.getOriginFieldName(),
                            streamFieldInfo.getOriginNodeName(), formatInfo);
                    FieldInfo outputField = new FieldInfo(streamFieldInfo.getFieldName(),
                            transformName, formatInfo);
                    return new FieldRelationShip(inputField, outputField);
                }).collect(Collectors.toList());
    }

    private static List<FieldRelationShip> createSplitterFieldRelationShips(List<StreamField> fieldList,
            String transformName, SplitterDefinition splitterDefinition, String preNodes) {
        Preconditions.checkNotEmpty(preNodes, "PreNodes of splitter should not be null");
        String preNode = preNodes.split(",")[0];
        List<SplitRule> splitRules = splitterDefinition.getSplitRules();
        Set<String> splitFields = Sets.newHashSet();
        List<FieldRelationShip> fieldRelationShips = splitRules.stream()
                .map(splitRule -> parseSplitRule(splitRule, splitFields, transformName, preNode))
                .reduce(Lists.newArrayList(), (list1, list2) -> {
                    list1.addAll(list2);
                    return list1;
                });
        List<StreamField> filteredFieldList = fieldList.stream()
                .filter(streamFieldInfo -> !splitFields.contains(streamFieldInfo.getFieldName()))
                .collect(Collectors.toList());
        fieldRelationShips.addAll(createFieldRelationShips(filteredFieldList, transformName));
        return fieldRelationShips;
    }

    private static List<FieldRelationShip> createReplacerFieldRelationShips(List<StreamField> fieldList,
            String transformName, StringReplacerDefinition replacerDefinition, String preNodes) {
        Preconditions.checkNotEmpty(preNodes, "PreNodes of splitter should not be null");
        String preNode = preNodes.split(",")[0];
        List<ReplaceRule> replaceRules = replacerDefinition.getReplaceRules();
        Set<String> replaceFields = Sets.newHashSet();
        List<FieldRelationShip> fieldRelationShips = replaceRules.stream()
                .map(replaceRule -> parseReplaceRule(replaceRule, replaceFields, transformName, preNode))
                .collect(Collectors.toList());
        fieldRelationShips = cascadeFunctionRelationShips(fieldRelationShips);
        List<StreamField> filteredFieldList = fieldList.stream()
                .filter(streamFieldInfo -> !replaceFields.contains(streamFieldInfo.getFieldName()))
                .collect(Collectors.toList());
        fieldRelationShips.addAll(createFieldRelationShips(filteredFieldList, transformName));
        return fieldRelationShips;
    }

    private static List<FieldRelationShip> cascadeFunctionRelationShips(List<FieldRelationShip> fieldRelationShips) {
        Map<String, List<CascadeFunction>> cascadeFunctions = Maps.newHashMap();
        Map<String, FieldInfo> targetFields = Maps.newHashMap();
        for (FieldRelationShip fieldRelationShip : fieldRelationShips) {
            CascadeFunction cascadeFunction = (CascadeFunction) fieldRelationShip.getInputField();
            String targetField = fieldRelationShip.getOutputField().getName();
            cascadeFunctions.computeIfAbsent(targetField, k -> Lists.newArrayList()).add(cascadeFunction);
            targetFields.put(targetField, fieldRelationShip.getOutputField());
        }
        List<FieldRelationShip> cascadeRelationShips = Lists.newArrayList();
        for (Map.Entry<String, List<CascadeFunction>> entry : cascadeFunctions.entrySet()) {
            String targetField = entry.getKey();
            CascadeFunctionWrapper functionWrapper = new CascadeFunctionWrapper(entry.getValue());
            FieldInfo targetFieldInfo = targetFields.get(targetField);
            cascadeRelationShips.add(new FieldRelationShip(functionWrapper, targetFieldInfo));
        }
        return cascadeRelationShips;
    }

    private static FieldRelationShip parseReplaceRule(ReplaceRule replaceRule, Set<String> replaceFields,
            String transformName, String preNode) {
        StreamField sourceField = replaceRule.getSourceField();
        final String fieldName = sourceField.getFieldName();
        String regex = replaceRule.getRegex();
        String targetValue = replaceRule.getTargetValue();
        ReplaceMode replaceMode = replaceRule.getMode();
        FieldInfo fieldInfo = FieldInfoUtils.parseStreamField(sourceField);
        fieldInfo.setNodeId(preNode);
        FieldInfo targetFieldInfo = new FieldInfo(fieldName, transformName,
                FieldInfoUtils.convertFieldFormat(FieldType.STRING.name()));
        replaceFields.add(fieldName);
        if (replaceMode == ReplaceMode.RELACE_ALL) {
            RegexpReplaceFunction regexpReplaceFunction = new RegexpReplaceFunction(fieldInfo,
                    new StringConstantParam(regex), new StringConstantParam(targetValue));
            return new FieldRelationShip(regexpReplaceFunction, targetFieldInfo);
        } else {
            RegexpReplaceFirstFunction regexpReplaceFirstFunction = new RegexpReplaceFirstFunction(fieldInfo,
                    new StringConstantParam(regex), new StringConstantParam(targetValue));
            return new FieldRelationShip(regexpReplaceFirstFunction, targetFieldInfo);
        }
    }

    private static List<FieldRelationShip> parseSplitRule(SplitRule splitRule, Set<String> splitFields,
            String transformName, String preNode) {
        StreamField sourceField = splitRule.getSourceField();
        FieldInfo fieldInfo = FieldInfoUtils.parseStreamField(sourceField);
        fieldInfo.setNodeId(preNode);
        String seperator = splitRule.getSeperator();
        List<String> targetSources = splitRule.getTargetFields();
        List<FieldRelationShip> splitRelationShips = Lists.newArrayList();
        for (int index = 0; index < targetSources.size(); index++) {
            SplitIndexFunction splitIndexFunction = new SplitIndexFunction(
                    fieldInfo, new StringConstantParam(seperator), new ConstantParam(index));
            FieldInfo targetFieldInfo = new FieldInfo(
                    targetSources.get(index), transformName, FieldInfoUtils.convertFieldFormat(FieldType.STRING.name())
            );
            splitFields.add(targetSources.get(index));
            splitRelationShips.add(new FieldRelationShip(splitIndexFunction, targetFieldInfo));
        }
        return splitRelationShips;
    }
}
