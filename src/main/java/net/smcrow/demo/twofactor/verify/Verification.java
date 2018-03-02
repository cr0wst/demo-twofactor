package net.smcrow.demo.twofactor.verify;

import org.springframework.data.annotation.PersistenceConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Date;

@Entity
public class Verification {
    @Id
    @Column(unique = true, nullable = false)
    private String phone;

    @Column(nullable = false)
    private String requestId;

    @Column(nullable = false)
    private Date expirationDate;

    @PersistenceConstructor
    public Verification() {
        // Empty constructor for JPA
    }

    public Verification(String phone, String requestId, Date expirationDate) {
        this.phone = phone;
        this.requestId = requestId;
        this.expirationDate = expirationDate;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Date getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(Date expirationDate) {
        this.expirationDate = expirationDate;
    }
}
