package bench;

import java.util.List;

public class BenchUser {
    private int id;
    private String name;
    private boolean active;
    private double score;
    private List<String> tags;
    private Address address;

    public BenchUser() {
    }

    public BenchUser(int id, String name, boolean active, double score, List<String> tags, Address address) {
        this.id = id;
        this.name = name;
        this.active = active;
        this.score = score;
        this.tags = tags;
        this.address = address;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }
}