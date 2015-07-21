package com.armedia.acm.plugins.casefile.model;

import com.armedia.acm.core.AcmObject;
import com.armedia.acm.data.AcmEntity;
import com.armedia.acm.plugins.addressable.model.ContactMethod;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name="acm_disposition")
public class Disposition implements Serializable, AcmObject, AcmEntity
{
    private static final long serialVersionUID = 7786267451369775524L;
    public static final String OBJECT_TYPE = "DISPOSITION";

    @Id
    @TableGenerator(name = "disposition_gen",
            table = "acm_disposition_id",
            pkColumnName = "cm_seq_name",
            valueColumnName = "cm_seq_num",
            pkColumnValue = "acm_disposition",
            initialValue = 100,
            allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "acm_disposition_id")
    @Column(name = "cm_disposition_id")
    private Long id;

    @Column(name = "cm_close_date")
    @Temporal(TemporalType.DATE)
    private Date closeDate;

    @Column(name = "cm_disposition_type")
    private String dispositionType;

    @Column(name = "cm_refer_ext_org_name")
    private String referExternalOrganizationName;

    @Column(name = "cm_refer_ext_person_name")
    private String referExternalContactPersonName;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "cm_refer_ext_contact_method_id")
    private ContactMethod referExternalContactMethod;

    @Column(name = "cm_existing_case_number")
    private String existingCaseNumber;

    @Column(name = "cm_disposition_created", nullable = false, insertable = true, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date created;

    @Column(name = "cm_disposition_creator", insertable = true, updatable = false)
    private String creator;

    @Column(name = "cm_disposition_modified", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date modified;

    @Column(name = "cm_disposition_modifier")
    private String modifier;

    @Override
    public String getObjectType() {
        return OBJECT_TYPE;
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Date getCloseDate()
    {
        return closeDate;
    }

    public void setCloseDate(Date closeDate)
    {
        this.closeDate = closeDate;
    }

    public String getDispositionType()
    {
        return dispositionType;
    }

    public void setDispositionType(String dispositionType)
    {
        this.dispositionType = dispositionType;
    }

    public String getReferExternalOrganizationName()
    {
        return referExternalOrganizationName;
    }

    public void setReferExternalOrganizationName(String referExternalOrganizationName)
    {
        this.referExternalOrganizationName = referExternalOrganizationName;
    }

    public String getReferExternalContactPersonName()
    {
        return referExternalContactPersonName;
    }

    public void setReferExternalContactPersonName(String referExternalContactPersonName)
    {
        this.referExternalContactPersonName = referExternalContactPersonName;
    }

    public ContactMethod getReferExternalContactMethod()
    {
        return referExternalContactMethod;
    }

    public void setReferExternalContactMethod(ContactMethod referExternalContactMethod)
    {
        this.referExternalContactMethod = referExternalContactMethod;
    }

    public String getExistingCaseNumber()
    {
        return existingCaseNumber;
    }

    public void setExistingCaseNumber(String existingCaseNumber)
    {
        this.existingCaseNumber = existingCaseNumber;
    }

    @Override
    public Date getCreated()
    {
        return created;
    }

    @Override
    public void setCreated(Date created)
    {
        this.created = created;
    }

    @Override
    public String getCreator()
    {
        return creator;
    }

    @Override
    public void setCreator(String creator)
    {
        this.creator = creator;
    }

    @Override
    public Date getModified()
    {
        return modified;
    }

    @Override
    public void setModified(Date modified)
    {
        this.modified = modified;
    }

    @Override
    public String getModifier()
    {
        return modifier;
    }

    @Override
    public void setModifier(String modifier)
    {
        this.modifier = modifier;
    }
}
