package imap.mail.downloader;

/**
 * Klasa DTO do przesyłania informacji o załączniku w formacie JSON
 */
public class AttachmentDTO {
    private String name;
    private String mimeType;
    private String size;
    private String data;
    private String encoding;

    public AttachmentDTO() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }
}