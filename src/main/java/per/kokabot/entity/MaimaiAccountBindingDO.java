package per.kokabot.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class MaimaiAccountBindingDO {

    private Long qqId;
    private String arcadeCredentialEncrypted;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
