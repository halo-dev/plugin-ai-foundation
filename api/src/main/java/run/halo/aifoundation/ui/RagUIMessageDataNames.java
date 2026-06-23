package run.halo.aifoundation.ui;

/**
 * Standard dynamic data names for RAG UI message integrations.
 */
public final class RagUIMessageDataNames {

    public static final String SOURCES = "rag-sources";

    public static final String RETRIEVED_DATA = "rag-retrieved-data";

    private RagUIMessageDataNames() {
    }

    public static String sourcesType() {
        return UIMessageDynamicNames.dataType(SOURCES);
    }

    public static String retrievedDataType() {
        return UIMessageDynamicNames.dataType(RETRIEVED_DATA);
    }
}
