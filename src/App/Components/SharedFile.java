package App.Components;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class SharedFile implements Serializable {
    private long id;
    private String name;
    private InetSocketAddress ownerAddress;
    private String title;
    private String author;
    private String location;
    private Boolean isShared;

    public SharedFile(long id, InetSocketAddress address, String title, String author, String location, Boolean isShared) {
        this.id = id;
        this.name = "B" + id;
        this.ownerAddress = address;
        this.title = title;
        this.author = author;
        this.location = location;
        this.isShared = isShared;
    }

    public long getId() {
        return id;
    }

    public void setId(long ip) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public InetSocketAddress getOwnerAddress() {
        return ownerAddress;
    }

    public void setOwnerAddress(InetSocketAddress ownerAddress) {
        this.ownerAddress = ownerAddress;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Boolean getIsShared() { return isShared; }

    public void setIsShared(Boolean shared) { isShared = shared; }

}