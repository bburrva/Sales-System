package com.theironyard.charlotte;

import java.util.List;

/**
 * Created by Ben on 1/15/17.
 */
public class Order {
    int id;
    int userId;
    boolean complete;
    List<Item> items;

    public Order(int userId, boolean complete) {
        this.userId = userId;
        this.complete = complete;
    }

    public Order(int id, int userId, boolean complete) {
        this.id = id;
        this.userId = userId;
        this.complete = complete;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }
}
