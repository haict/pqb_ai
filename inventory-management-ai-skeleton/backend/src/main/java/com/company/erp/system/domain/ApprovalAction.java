package com.company.erp.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** Một lần xử lý yêu cầu duyệt (gửi, duyệt, từ chối, hủy); chỉ INSERT. */
@Entity
@Immutable
@Table(name = "approval_actions")
public class ApprovalAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "step_no")
    private Integer stepNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private ApprovalActionType action;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "on_behalf_of_user_id")
    private Long onBehalfOfUserId;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "acted_at", nullable = false)
    private Instant actedAt;

    protected ApprovalAction() {
    }

    public ApprovalAction(Long requestId, Integer stepNo, ApprovalActionType action, Long actorUserId,
                          Long onBehalfOfUserId, String comment, Instant actedAt) {
        this.requestId = requestId;
        this.stepNo = stepNo;
        this.action = action;
        this.actorUserId = actorUserId;
        this.onBehalfOfUserId = onBehalfOfUserId;
        this.comment = comment;
        this.actedAt = actedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Integer getStepNo() {
        return stepNo;
    }

    public ApprovalActionType getAction() {
        return action;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public Long getOnBehalfOfUserId() {
        return onBehalfOfUserId;
    }

    public String getComment() {
        return comment;
    }

    public Instant getActedAt() {
        return actedAt;
    }
}
