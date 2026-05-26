package run.halo.aifoundation;

public class TextPart extends ModelMessagePart {

    public TextPart() {
        setType(TYPE_TEXT);
    }

    public TextPart(String text) {
        setType(TYPE_TEXT);
        setText(text);
    }
}
