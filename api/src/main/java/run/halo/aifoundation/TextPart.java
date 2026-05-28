package run.halo.aifoundation;

public class TextPart extends ModelMessagePart {

    public TextPart() {
        setType(PartType.TEXT);
    }

    public TextPart(String text) {
        setType(PartType.TEXT);
        setText(text);
    }
}
