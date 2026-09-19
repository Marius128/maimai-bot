package per.kokabot.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class LxnsOAuthBindingDO {

    private Long qqId;
    private String subject;
    private String username;
    private String accessTokenEncrypted;
    private String refreshTokenEncrypted;
    private Timestamp accessTokenExpiresAt;
    private Timestamp refreshTokenExpiresAt;
    private String scope;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
