package com.sap.cloud.lm.sl.mta.parsers.v3_0;

import static com.sap.cloud.lm.sl.mta.handlers.v3_0.Schemas.MODULE_TYPE;

import java.util.Map;

import com.sap.cloud.lm.sl.common.ParsingException;
import com.sap.cloud.lm.sl.mta.model.v3_0.ModuleType;
import com.sap.cloud.lm.sl.mta.model.v3_0.ModuleType.ModuleTypeBuilder;
import com.sap.cloud.lm.sl.mta.schema.MapElement;

public class ModuleTypeParser extends com.sap.cloud.lm.sl.mta.parsers.v2_0.ModuleTypeParser {

    public ModuleTypeParser(Map<String, Object> source) {
        super(MODULE_TYPE, source);
    }

    protected ModuleTypeParser(MapElement schema, Map<String, Object> source) {
        super(schema, source);
    }

    @Override
    public ModuleType parse() throws ParsingException {
        ModuleTypeBuilder builder = new ModuleTypeBuilder();
        builder.setName(getName());
        builder.setDeployer(getDeployer());
        builder.setProperties(getProperties());
        builder.setParameters(getParameters());
        return builder.build();
    }

}
