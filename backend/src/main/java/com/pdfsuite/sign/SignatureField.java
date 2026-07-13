package com.pdfsuite.sign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** A field placed on the document; x,y,w,h are 0..1 page fractions, origin top-left. */
@Entity
@Table(name = "signature_field")
public class SignatureField {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID requestId;

    @Column(nullable = false)
    private UUID signerId;

    @Column(nullable = false)
    private int page;

    /** SIGNATURE | NAME | DATE | TEXT */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false) private float x;
    @Column(nullable = false) private float y;
    @Column(nullable = false) private float w;
    @Column(nullable = false) private float h;

    @Column(length = 2000)
    private String value;

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public void setRequestId(UUID requestId) { this.requestId = requestId; }
    public UUID getSignerId() { return signerId; }
    public void setSignerId(UUID signerId) { this.signerId = signerId; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public float getX() { return x; }
    public void setX(float x) { this.x = x; }
    public float getY() { return y; }
    public void setY(float y) { this.y = y; }
    public float getW() { return w; }
    public void setW(float w) { this.w = w; }
    public float getH() { return h; }
    public void setH(float h) { this.h = h; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
