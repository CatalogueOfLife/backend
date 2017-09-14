package org.col.api;

import java.net.URI;
import java.util.Objects;

/**
 * Simplified literature reference class for proof of concept only.
 */
public class Reference {
    private Integer key;
    private String title;
    private String author;
    private Integer year;
    private URI link;
    private String identifier;

    public Integer getKey() {
        return key;
    }

    public void setKey(Integer key) {
        this.key = key;
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

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public URI getLink() {
        return link;
    }

    public void setLink(URI link) {
        this.link = link;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reference reference = (Reference) o;
        return Objects.equals(key, reference.key) &&
                Objects.equals(title, reference.title) &&
                Objects.equals(author, reference.author) &&
                Objects.equals(year, reference.year) &&
                Objects.equals(link, reference.link) &&
                Objects.equals(identifier, reference.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, title, author, year, link, identifier);
    }
}
