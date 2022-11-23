package uk.nhs.adaptors.scr.models.xml;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SocialOrPersonalCircumstance {
    private String idRoot;
    private String codeCode;
    private String codeDisplayName;
    private String statusCodeCode;
    private String effectiveTimeLow;
    private String effectiveTimeHigh;
    private String effectiveTimeCenter;
}
