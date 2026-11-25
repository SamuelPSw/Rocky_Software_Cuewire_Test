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

import static org.hamcrest.Matchers.containsString;
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
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + id + ")].remindedAt").isNotEmpty());
        mvc.perform(post(path + "/cancel").header(ROLE, "TUTOR")).andExpect(status().isConflict());
        mvc.perform(put(path + "/payment").header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":0}")).andExpect(status().isOk());
        mvc.perform(post(path + "/cancel").header(ROLE, "TUTOR"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cancelled").value(true))
            .andExpect(jsonPath("$.balance").value(0.0));
    }

    @Test
    void savesPrivateDraftsAndSharesNotesAndPdfsThroughTheCompletedSession() throws Exception {
        String completed = INPUT.replace("2027-01-01T15:00:00Z", "2020-01-01T15:00:00Z");
        String path = SESSIONS + "/" + createSession(completed);
        String notes = path + "/notes";
        String future = SESSIONS + "/" + createSession(INPUT.replace("2027-", "2099-")) + "/notes";
        String cancelled = SESSIONS + "/" + createSession(completed);
        mvc.perform(post(cancelled + "/cancel").header(ROLE, "TUTOR")).andExpect(status().isOk());

        mvc.perform(put(future).header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"Too soon\"}")).andExpect(status().isConflict());
        mvc.perform(multipart(cancelled + "/notes/share").param("text", "Cancelled")
            .header(ROLE, "TUTOR")).andExpect(status().isConflict());
        mvc.perform(put(notes).header(ROLE, "FAMILY").contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"Family edit\"}")).andExpect(status().isForbidden());
        mvc.perform(multipart(notes + "/share").param("text", "Family edit")
            .header(ROLE, "FAMILY")).andExpect(status().isForbidden());
        mvc.perform(put(notes).header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"First draft\"}")).andExpect(status().isOk());
        mvc.perform(get(notes).header(ROLE, "TUTOR"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("First draft"));
        mvc.perform(get(notes).header(ROLE, "FAMILY")).andExpect(status().isNotFound());

        byte[] pdf = samplePdf();
        var file = new MockMultipartFile("file", "recap.pdf", "application/pdf", pdf);
        var shared = mvc.perform(multipart(notes + "/share").file(file).param("text", "Shared recap")
            .header(ROLE, "TUTOR")).andExpect(status().isOk()).andReturn();
        long pdfId = mapper.readTree(shared.getResponse().getContentAsString()).get("pdf").get("id").asLong();
        mvc.perform(get(PDFS + "/" + pdfId).header(ROLE, "FAMILY")).andExpect(status().isNotFound());
        mvc.perform(get(PDFS).header(ROLE, "FAMILY"))
            .andExpect(jsonPath("$[?(@.id == " + pdfId + ")]").isEmpty());
        mvc.perform(get(notes + "/pdf").header(ROLE, "FAMILY"))
            .andExpect(status().isOk()).andExpect(content().bytes(pdf));

        mvc.perform(put(notes).header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"Revised draft\"}")).andExpect(status().isOk());
        mvc.perform(get(notes).header(ROLE, "FAMILY"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("Shared recap"))
            .andExpect(jsonPath("$.pdf.name").value("recap.pdf"));
        var invalid = new MockMultipartFile("file", "broken.pdf", "application/pdf", new byte[1]);
        mvc.perform(multipart(notes + "/share").file(invalid).param("text", "Unpublished")
            .header(ROLE, "TUTOR")).andExpect(status().isBadRequest());
        mvc.perform(get(notes).header(ROLE, "TUTOR"))
            .andExpect(jsonPath("$.text").value("Revised draft"));
        mvc.perform(multipart(notes + "/share").param("text", "Revised draft").param("removePdf", "true")
            .header(ROLE, "TUTOR")).andExpect(status().isOk());
        mvc.perform(get(notes).header(ROLE, "FAMILY"))
            .andExpect(jsonPath("$.text").value("Revised draft")).andExpect(jsonPath("$.pdf").doesNotExist());
        mvc.perform(get(notes + "/pdf").header(ROLE, "FAMILY")).andExpect(status().isNotFound());
    }

    private long createSession(String input) throws Exception {
        var result = mvc.perform(post(SESSIONS).header(ROLE, "TUTOR").contentType(MediaType.APPLICATION_JSON)
            .content(input)).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private static byte[] samplePdf() throws Exception {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    @Test
    void validatesPdfAndLetsFamilyReadByIdentifier() throws Exception {
        byte[] pdf = samplePdf();
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
