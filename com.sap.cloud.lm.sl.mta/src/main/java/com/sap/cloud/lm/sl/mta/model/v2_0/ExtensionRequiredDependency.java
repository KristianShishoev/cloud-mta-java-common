package com.sap.cloud.lm.sl.mta.model.v2_0;

import static com.sap.cloud.lm.sl.common.util.CommonUtil.getOrDefault;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sap.cloud.lm.sl.common.util.MapUtil;
import com.sap.cloud.lm.sl.mta.builders.Builder;
import com.sap.cloud.lm.sl.mta.model.VisitableElement;
import com.sap.cloud.lm.sl.mta.model.ElementContext;
import com.sap.cloud.lm.sl.mta.model.ParametersContainer;
import com.sap.cloud.lm.sl.mta.model.PropertiesContainer;
import com.sap.cloud.lm.sl.mta.model.Visitor;
import com.sap.cloud.lm.sl.mta.parsers.v2_0.ExtensionRequiredDependencyParser;
import com.sap.cloud.lm.sl.mta.util.YamlElement;

public class ExtensionRequiredDependency implements VisitableElement, PropertiesContainer, ParametersContainer {

    @YamlElement(ExtensionRequiredDependencyParser.NAME)
    private String name;
    @YamlElement(ExtensionRequiredDependencyParser.PROPERTIES)
    private Map<String, Object> properties;
    @YamlElement(ExtensionRequiredDependencyParser.PARAMETERS)
    private Map<String, Object> parameters;

    protected ExtensionRequiredDependency() {

    }

    public String getName() {
        return name;
    }

    @Override
    public Map<String, Object> getProperties() {
        return MapUtil.unmodifiable(properties);
    }

    @Override
    public Map<String, Object> getParameters() {
        return MapUtil.unmodifiable(parameters);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = new LinkedHashMap<>(properties);
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = new LinkedHashMap<>(parameters);
    }

    @Override
    public void accept(ElementContext context, Visitor visitor) {
        visitor.visit(context, this);
    }

    public static class ExtensionRequiredDependencyBuilder implements Builder<ExtensionRequiredDependency> {

        protected String name;
        protected Map<String, Object> properties;
        protected Map<String, Object> parameters;

        @Override
        public ExtensionRequiredDependency build() {
            ExtensionRequiredDependency result = new ExtensionRequiredDependency();
            result.setName(name);
            result.setProperties(getOrDefault(properties, Collections.<String, Object> emptyMap()));
            result.setParameters(getOrDefault(parameters, Collections.<String, Object> emptyMap()));
            return result;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setProperties(Map<String, Object> properties) {
            this.properties = properties;
        }

        public void setParameters(Map<String, Object> parameters) {
            this.parameters = parameters;
        }

    }

}
