package th.ac.kmitl.connect;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BoardApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void categoriesAreReadableWithoutLogin() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
    }

    @Test
    void boardsAreReadableWithoutLogin() throws Exception {
        mvc.perform(get("/api/boards")).andExpect(status().isOk());
    }

    @Test
    void creatingAPostWithoutLoginIsRejected() throws Exception {
        mvc.perform(post("/api/boards/1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsAreProtected() throws Exception {
        mvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"displayOrder\":0}"))
                .andExpect(status().isUnauthorized());
    }
}
