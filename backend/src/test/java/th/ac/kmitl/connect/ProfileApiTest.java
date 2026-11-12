package th.ac.kmitl.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import th.ac.kmitl.connect.entity.Role;
import th.ac.kmitl.connect.entity.User;
import th.ac.kmitl.connect.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    private final ObjectMapper json = new ObjectMapper();

    /** A verified member made directly in the database, so no OTP step is needed. */
    private User member(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode("password123"));
        u.setDisplayName("สมาชิกทดสอบ");
        u.setRole(Role.STUDENT);
        u.setVerified(true);
        return users.save(u);
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    private JsonNode read(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void profileShowsCountsAndHidesEmailFromOthers() throws Exception {
        User u = member("profile-view@kmitl.ac.th");
        MockHttpSession s = login(u.getEmail(), "password123");
        long boardId = read(mvc.perform(get("/api/boards")).andReturn()).get(0).get("id").asLong();
        mvc.perform(post("/api/boards/" + boardId + "/posts").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"กระทู้ของฉัน\",\"content\":\"ทดสอบหน้าโปรไฟล์\"}"))
                .andExpect(status().isOk());

        JsonNode mine = read(mvc.perform(get("/api/users/" + u.getId()).session(s))
                .andExpect(status().isOk()).andReturn());
        assertTrue(mine.get("me").asBoolean());
        assertEquals(u.getEmail(), mine.get("email").asText());
        assertEquals(1, mine.get("postCount").asLong());

        JsonNode publicView = read(mvc.perform(get("/api/users/" + u.getId()))
                .andExpect(status().isOk()).andReturn());
        assertFalse(publicView.get("me").asBoolean());
        assertTrue(publicView.get("email").isNull());

        JsonNode posts = read(mvc.perform(get("/api/users/" + u.getId() + "/posts"))
                .andExpect(status().isOk()).andReturn());
        assertEquals("กระทู้ของฉัน", posts.get("items").get(0).get("title").asText());

        mvc.perform(get("/api/users/999999")).andExpect(status().isNotFound());
    }

    @Test
    void memberCanRenameThemselves() throws Exception {
        User u = member("profile-rename@kmitl.ac.th");
        MockHttpSession s = login(u.getEmail(), "password123");

        mvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"ชื่อใหม่\"}"))
                .andExpect(status().isUnauthorized());

        JsonNode me = read(mvc.perform(put("/api/users/me").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"ชื่อใหม่\"}"))
                .andExpect(status().isOk()).andReturn());
        assertEquals("ชื่อใหม่", me.get("displayName").asText());

        mvc.perform(put("/api/users/me").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordChangeNeedsTheCurrentPassword() throws Exception {
        User u = member("profile-password@kmitl.ac.th");
        MockHttpSession s = login(u.getEmail(), "password123");

        mvc.perform(put("/api/users/me/password").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-pass\",\"newPassword\":\"newpassword456\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/users/me/password").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword456\"}"))
                .andExpect(status().isOk());

        login(u.getEmail(), "newpassword456");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
    }
}
