package dev.tutorme;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:tutorme-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "tutorme.seed=false"
})
@AutoConfigureMockMvc
class TutoringApiTest {
    private static final String ROLE = "X-Demo-Role";
    private static final String SESSIONS = "/api/sessions";
    private static final String PDFS = "/api/handouts";
    private static final String INPUT = """
        {"subject":"Algebra","startsAt":"2027-01-01T15:00:00Z","minutes":45,
         "hourlyRate":60,"zoomUrl":"https://zoom.us/j/123"}
        """;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void tutorManagesSessionAndFamilySeesItsReminder() throws Exception {
        mvc.perform(get(SESSIONS)).andExpect(status().isForbidden());
        mvc.perform(post(SESSIONS).header(ROLE, "FAMILY").contentType(MediaType.APPLICATION_JSON).content(INPUT))
            .andExpect(status().isForbidden());
        mvc.perform(post(SESSIONS).header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON)
            .content(INPUT.replace("45", "45.5"))).andExpect(status().isBadRequest());

        var created = mvc.perform(post(SESSIONS).header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON).content(INPUT))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.balance").value(45.0)).andReturn();
        long id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String path = SESSIONS + "/" + id;
        mvc.perform(put(path).header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON)
            .content(INPUT.replace("Algebra", "Geometry")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.subject").value("Geometry"));
        mvc.perform(put(path + "/payment").header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":10}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(35.0));
        mvc.perform(post(path + "/reminder").header(ROLE, "TUTOR"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.remindedAt").isNotEmpty());
        mvc.perform(get(SESSIONS).header(ROLE, "FAMILY"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + id + ")].remindedAt", hasSize(1)));
        mvc.perform(post(path + "/cancel").header(ROLE, "TUTOR")).andExpect(status().isConflict());
        mvc.perform(put(path + "/payment").header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":0}")).andExpect(status().isOk());
        mvc.perform(post(path + "/cancel").header(ROLE, "TUTOR"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cancelled").value(true))
            .andExpect(jsonPath("$.balance").value(0.0));
    }

    @Test
    void familyReadsPublishedNotesOnlyForCompletedSessions() throws Exception {
        String text = "Practised factoring.\nReview exercise 3.";
        String notes = mapper.writeValueAsString(Map.of("text", text));
        String past = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        var created = mvc.perform(post(SESSIONS).header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON)
            .content(INPUT.replace("2027-01-01T15:00:00Z", past)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.notes").doesNotExist()).andReturn();
        long id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String path = SESSIONS + "/" + id;
        mvc.perform(put(path + "/notes").header(ROLE, "FAMILY").contentType(MediaType.APPLICATION_JSON).content(notes))
            .andExpect(status().isForbidden());
        var shared = mvc.perform(put(path + "/notes").header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON)
            .content(notes)).andExpect(status().isOk()).andReturn();
        String revision = mapper.readTree(shared.getResponse().getContentAsString()).get("notes").get("revision").asText();
        mvc.perform(get(SESSIONS).header(ROLE, "FAMILY"))
            .andExpect(jsonPath("$[?(@.id == " + id + ")].notes.text", contains(text)))
            .andExpect(jsonPath("$[?(@.id == " + id + ")].notes.revision", contains(revision)));
        mvc.perform(put(path + "/notes").header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON).content(notes))
            .andExpect(status().isOk()).andExpect(jsonPath("$.notes.revision").value(revision));
        mvc.perform(put(path + "/notes").header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"Updated notes\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.notes.revision", not(revision)));
        String inProgress = Instant.now().minus(1, ChronoUnit.MINUTES).toString();
        mvc.perform(put(path).header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON)
            .content(INPUT.replace("2027-01-01T15:00:00Z", inProgress)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.notes").doesNotExist());
        mvc.perform(get(SESSIONS).header(ROLE, "FAMILY"))
            .andExpect(jsonPath("$[?(@.id == " + id + ")].notes.text").isEmpty());
        mvc.perform(put(path + "/notes").header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON).content(notes))
            .andExpect(status().isConflict());
        mvc.perform(post(path + "/cancel").header(ROLE, "TUTOR")).andExpect(status().isOk());
        mvc.perform(put(path + "/notes").header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON).content(notes))
            .andExpect(status().isConflict());
    }

    @Test
    void validatesPdfAndLetsFamilyReadByIdentifier() throws Exception {
        byte[] pdf;
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            pdf = output.toByteArray();
        }
        var file = new MockMultipartFile("file", "../worksheet.pdf", "application/pdf", pdf);
        mvc.perform(multipart(PDFS).file(file).header(ROLE, "FAMILY")).andExpect(status().isForbidden());
        mvc.perform(multipart(PDFS).file(new MockMultipartFile("file", "fake.pdf", "application/pdf", new byte[1]))
            .header(ROLE, "TUTOR")).andExpect(status().isBadRequest());
        byte[] damaged = "%PDF-1.7\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(multipart(PDFS).file(new MockMultipartFile("file", "damaged.pdf", "application/pdf", damaged))
            .header(ROLE, "TUTOR")).andExpect(status().isBadRequest());
        mvc.perform(multipart(PDFS).file(new MockMultipartFile("file", "large.pdf", "application/pdf",
            new byte[5 * 1024 * 1024 + 1])).header(ROLE, "TUTOR")).andExpect(status().isPayloadTooLarge());
        var uploaded = mvc.perform(multipart(PDFS).file(file).header(ROLE, "TUTOR"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("worksheet.pdf")).andReturn();
        long id = mapper.readTree(uploaded.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get(PDFS).header(ROLE, "FAMILY")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("worksheet.pdf")).andExpect(jsonPath("$[0].content").doesNotExist());
        mvc.perform(get(PDFS + "/" + id).header(ROLE, "FAMILY"))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", containsString("inline")))
            .andExpect(content().bytes(pdf));
        mvc.perform(get(PDFS + "/" + id).param("download", "true").header(ROLE, "FAMILY"))
            .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", containsString("attachment")));
    }
}
