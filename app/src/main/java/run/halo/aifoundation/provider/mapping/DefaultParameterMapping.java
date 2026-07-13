package run.halo.aifoundation.provider.mapping;

import run.halo.aifoundation.extension.ModelParameterMappings;

public record DefaultParameterMapping(ModelParameterMappings.Mode mode, String template) {

    public static DefaultParameterMapping template(String template) {
        return new DefaultParameterMapping(ModelParameterMappings.Mode.TEMPLATE, template);
    }

    public static DefaultParameterMapping unsupported() {
        return new DefaultParameterMapping(ModelParameterMappings.Mode.UNSUPPORTED, null);
    }
}
