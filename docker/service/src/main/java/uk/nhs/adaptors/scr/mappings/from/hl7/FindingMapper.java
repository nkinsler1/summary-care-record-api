package uk.nhs.adaptors.scr.mappings.from.hl7;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.EncounterParticipantComponent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import uk.nhs.adaptors.scr.utils.FhirHelper;
import uk.nhs.adaptors.scr.utils.XmlUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.hl7.fhir.r4.model.Encounter.EncounterStatus.FINISHED;
import static uk.nhs.adaptors.scr.mappings.from.hl7.PerformerParticipationMode.getParticipationModeDisplay;
import static uk.nhs.adaptors.scr.utils.FhirHelper.randomUUID;

@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class FindingMapper implements XmlToFhirMapper {

    private static final String BASE_PATH =
        "//QUPC_IN210000UK04/ControlActEvent/subject//GPSummary/pertinentInformation2/pertinentCREType/component/UKCT_MT144043UK02.Finding";

    private static final String FINDING_ID_XPATH = "./id/@root";
    private static final String FINDING_CODE_CODE_XPATH = "./code/@code";
    private static final String FINDING_CODE_DISPLAY_NAME_XPATH = "./code/@displayName";
    private static final String FINDING_STATUS_CODE_XPATH = "./statusCode/@code";
    private static final String FINDING_EFFECTIVE_TIME_LOW_XPATH = "./effectiveTime/low/@value";
    private static final String FINDING_EFFECTIVE_TIME_HIGH_XPATH = "./effectiveTime/low/@value";
    private static final String FINDING_EFFECTIVE_TIME_CENTRE_XPATH = "./effectiveTime/centre/@value";
    private static final String ENCOUNTER_PARTICIPATION_CODE_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ParticipationType";
    private static final String FINDING_AUTHOR_XPATH = "./author";
    private static final String FINDING_INFORMANT_XPATH = "./informant";
    private static final String FINDING_PERFORMER_XPATH = "./performer";
    private static final String FINDING_PARTICIPANT_TIME_XPATH = "./time/@value";
    private static final String FINDING_PERFORMER_MODE_CODE_XPATH = "./modeCode/@code";
    private static final String PERFORMER_EXTENSION_URL = "https://fhir.nhs.uk/StructureDefinition/Extension-SCR-ModeCode";
    private static final String ENCOUNTER_PARTICIPATION_MODE_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ParticipationMode";
    private static final String ENCOUNTER_CLASS_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-NullFlavor";

    private final ParticipantMapper participantMapper;

    @SneakyThrows
    public List<Resource> map(Node document) {
        var resources = new ArrayList<Resource>();
        for (var node : XmlUtils.getNodesByXPath(document, BASE_PATH)) {
            var findingId =
                XmlUtils.getValueByXPath(node, FINDING_ID_XPATH);
            var findingCodeCode =
                XmlUtils.getValueByXPath(node, FINDING_CODE_CODE_XPATH);
            var findingCodeDisplayName =
                XmlUtils.getValueByXPath(node, FINDING_CODE_DISPLAY_NAME_XPATH);
            var findingStatusCode =
                XmlUtils.getValueByXPath(node, FINDING_STATUS_CODE_XPATH);
            var findingEffectiveTimeLow =
                XmlUtils.getOptionalValueByXPath(node, FINDING_EFFECTIVE_TIME_LOW_XPATH)
                    .map(XmlToFhirMapper::parseDate);
            var findingEffectiveTimeHigh =
                XmlUtils.getOptionalValueByXPath(node, FINDING_EFFECTIVE_TIME_HIGH_XPATH)
                    .map(XmlToFhirMapper::parseDate);
            var findingEffectiveTimeCentre =
                XmlUtils.getOptionalValueByXPath(node, FINDING_EFFECTIVE_TIME_CENTRE_XPATH)
                    .map(XmlToFhirMapper::parseDate);

            var observation = new Observation();
            observation.setId(FhirHelper.randomUUID());
            observation.addIdentifier(new Identifier().setValue(findingId));
            observation.setCode(new CodeableConcept().addCoding(new Coding()
                .setCode(findingCodeCode)
                .setSystem(SNOMED_SYSTEM)
                .setDisplay(findingCodeDisplayName)));
            observation.setStatus(mapStatus(findingStatusCode));
            if (findingEffectiveTimeLow.isPresent() || findingEffectiveTimeHigh.isPresent()) {
                var period = new Period();
                findingEffectiveTimeLow.ifPresent(period::setStart);
                findingEffectiveTimeHigh.ifPresent(period::setEnd);
                observation.setEffective(period);
            } else {
                findingEffectiveTimeCentre
                    .map(DateTimeType::new)
                    .ifPresent(observation::setEffective);
            }

            resources.add(observation);
            mapEncounter(node, observation, resources);
        }
        return resources;
    }

    private void mapEncounter(Node finding, Observation observation, List<Resource> resources) {
        Optional<Node> author = XmlUtils.getOptionalNodeByXpath(finding, FINDING_AUTHOR_XPATH);
        Optional<Node> informant = XmlUtils.getOptionalNodeByXpath(finding, FINDING_INFORMANT_XPATH);
        List<Node> performers = XmlUtils.getNodesByXPath(finding, FINDING_PERFORMER_XPATH);
        if (author.isPresent() || informant.isPresent() || !performers.isEmpty()) {
            Encounter encounter = new Encounter();
            encounter.setStatus(FINISHED);
            encounter.setClass_(new Coding()
                .setCode("UNK")
                .setSystem(ENCOUNTER_CLASS_SYSTEM)
                .setDisplay("Unknown"));
            encounter.setId(randomUUID());
            performers.stream().forEach(performerNode -> mapPerformer(resources, encounter, performerNode));
            author.ifPresent(authorNode -> mapAuthor(resources, encounter, authorNode));
            informant.ifPresent(informantNode -> mapInformant(resources, encounter, informantNode));
            observation.setEncounter(new Reference(encounter));
            resources.add(encounter);
        }
    }

    private void mapPerformer(List<Resource> resources, Encounter encounter, Node performer) {
        Date time = XmlToFhirMapper.parseDate(XmlUtils.getValueByXPath(performer, FINDING_PARTICIPANT_TIME_XPATH));
        participantMapper.map(performer)
            .stream()
            .peek(it -> resources.add(it))
            .filter(it -> it instanceof PractitionerRole)
            .map(Reference::new)
            .forEach(it -> {
                EncounterParticipantComponent participant = new EncounterParticipantComponent();
                String modeCode = XmlUtils.getValueByXPath(performer, FINDING_PERFORMER_MODE_CODE_XPATH);
                participant.addExtension(PERFORMER_EXTENSION_URL, new CodeableConcept(
                    new Coding()
                        .setSystem(ENCOUNTER_PARTICIPATION_MODE_SYSTEM)
                        .setCode(modeCode)
                        .setDisplay(getParticipationModeDisplay(modeCode))));
                encounter.addParticipant(participant
                    .setPeriod(new Period().setStart(time))
                    .addType(getParticipationType("PRF", "performer"))
                    .setIndividual(it));
            });
    }

    private void mapInformant(List<Resource> resources, Encounter encounter, Node informant) {
        Date time = XmlToFhirMapper.parseDate(XmlUtils.getValueByXPath(informant, FINDING_PARTICIPANT_TIME_XPATH));
        participantMapper.map(informant)
            .stream()
            .peek(it -> resources.add(it))
            .filter(it -> it instanceof PractitionerRole || it instanceof RelatedPerson)
            .map(Reference::new)
            .forEach(it -> encounter.addParticipant(new EncounterParticipantComponent()
                .setPeriod(new Period().setStart(time))
                .addType(getParticipationType("INF", "informant"))
                .setIndividual(it)));
    }

    private void mapAuthor(List<Resource> resources, Encounter encounter, Node author) {
        Date time = XmlToFhirMapper.parseDate(XmlUtils.getValueByXPath(author, FINDING_PARTICIPANT_TIME_XPATH));
        participantMapper.map(author)
            .stream()
            .peek(it -> resources.add(it))
            .filter(it -> it instanceof PractitionerRole)
            .map(Reference::new)
            .forEach(it -> encounter.addParticipant(new EncounterParticipantComponent()
                .setPeriod(new Period().setStart(time))
                .addType(getParticipationType("AUT", "author"))
                .setIndividual(it)));
    }

    private CodeableConcept getParticipationType(String inf, String informant) {
        return new CodeableConcept(new Coding()
            .setCode(inf)
            .setSystem(ENCOUNTER_PARTICIPATION_CODE_SYSTEM)
            .setDisplay(informant));
    }

    private Observation.ObservationStatus mapStatus(String statusCode) {
        switch (statusCode) {
            case "normal":
            case "active":
            case "completed":
                return Observation.ObservationStatus.FINAL;
            case "nullified":
                return Observation.ObservationStatus.ENTEREDINERROR;
            default:
                throw new IllegalArgumentException(statusCode);
        }
    }
}
