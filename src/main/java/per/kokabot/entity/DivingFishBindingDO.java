package per.kokabot.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class DivingFishBindingDO {

    private Long qqId;
    private String importTokenEncrypted;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
