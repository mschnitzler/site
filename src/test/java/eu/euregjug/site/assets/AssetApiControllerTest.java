/*
 * Copyright 2016 EuregJUG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.euregjug.site.assets;

import com.mongodb.gridfs.GridFSDBFile;
import eu.euregjug.site.config.SecurityTestConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import java.util.Locale;
import org.apache.tika.Tika;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.joor.Reflect;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * @author Michael J. Simons, 2016-07-15
 */
@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@WebMvcTest(AssetApiController.class)
@EnableSpringDataWebSupport // Needed to enable resolving of Pageable and other parameters
@Import(SecurityTestConfig.class) // Needed to get rid of default CSRF protection
@AutoConfigureRestDocs(
        outputDir = "target/generated-snippets",
        uriHost = "euregjug.eu",
        uriPort = 80
)
public class AssetApiControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AssetApiController controller;

    @MockBean
    private GridFsTemplate gridFsTemplate;

    @Test
    public void createShouldThrowException() throws Exception {
        final MockMultipartFile multipartFile = new MockMultipartFile("assetData", this.getClass().getResourceAsStream("/eu/euregjug/site/assets/asset.png"));
        when(this.gridFsTemplate.findOne(any(Query.class))).thenReturn(mock(GridFSDBFile.class));

        mvc
                .perform(
                        fileUpload("/api/assets")
                        .file(multipartFile)
                )
                .andExpect(status().isConflict())
                .andExpect(content().string(""));

        verify(this.gridFsTemplate).findOne(any(Query.class));
        verifyNoMoreInteractions(this.gridFsTemplate);
    }

    @Test
    public void createShouldWork() throws Exception {
        final MockMultipartFile multipartFile = new MockMultipartFile("assetData", "asset.png", null, this.getClass().getResourceAsStream("/eu/euregjug/site/assets/asset.png"));

        when(this.gridFsTemplate.findOne(any(Query.class))).thenReturn(null);

        mvc
                .perform(
                        fileUpload("/api/assets")
                        .file(multipartFile)
                )
                .andExpect(status().isCreated())
                .andExpect(content().string("asset.png"))
                .andDo(document("api/assets/create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint())
                ));

        verify(this.gridFsTemplate).findOne(any(Query.class));
        verify(this.gridFsTemplate).store(any(InputStream.class), eq("asset.png"), eq("image/png"));
        verifyNoMoreInteractions(this.gridFsTemplate);
    }

    @Test
    @DirtiesContext
    public void failedMimetypeDetectionShouldWork() throws Exception {
        final Reflect controllerReflect = Reflect.on(this.controller);
        // Much more evil isn't possible, i guess... DirtiesContext!!!!
        Tika tika = controllerReflect.field("tika").get();
        tika = spy(tika);
        when(tika.detect(any(InputStream.class), any(String.class))).thenThrow(IOException.class);
        controllerReflect.set("tika", tika);

        final MockMultipartFile multipartFile = new MockMultipartFile("assetData", "asset.png", null, this.getClass().getResourceAsStream("/eu/euregjug/site/assets/asset.png"));
        when(this.gridFsTemplate.findOne(any(Query.class))).thenReturn(null);

        mvc
                .perform(
                        fileUpload("/api/assets")
                        .file(multipartFile)
                )
                .andExpect(status().isCreated())
                .andExpect(content().string("asset.png"));

        verify(this.gridFsTemplate).findOne(any(Query.class));
        verify(this.gridFsTemplate).store(any(InputStream.class), eq("asset.png"), isNull(String.class));
        verifyNoMoreInteractions(this.gridFsTemplate);
    }

    @Test
    public void getShouldThrowException() throws Exception {
        when(this.gridFsTemplate.findOne(any(Query.class))).thenReturn(null);

        mvc
                .perform(get("/api/assets/notthere.jpg"))
                .andExpect(status().isNotFound());

        verify(this.gridFsTemplate).findOne(any(Query.class));
        verifyNoMoreInteractions(this.gridFsTemplate);
    }

    @Test
    public void getShoudWork() throws Exception {
        GridFSDBFile file = mock(GridFSDBFile.class);
        when(file.getContentType()).thenReturn("text/plain");
        when(file.getFilename()).thenReturn("helloword.txt");
        when(file.writeTo(any(OutputStream.class))).then(invocation -> {
            final OutputStream out = invocation.getArgumentAt(0, OutputStream.class);
            final byte[] message = "Hello, World!".getBytes(StandardCharsets.UTF_8);
            out.write(message);
            return (long) message.length;
        });

        when(this.gridFsTemplate.findOne(any(Query.class))).thenReturn(file);

        final DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                .appendText(DAY_OF_WEEK, TextStyle.SHORT)
                .appendLiteral(", ")
                .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
                .appendLiteral(' ')
                .appendText(MONTH_OF_YEAR, TextStyle.SHORT)
                .appendLiteral(" .*").toFormatter(Locale.ENGLISH);
        mvc
                .perform(get("/api/assets/message.txt"))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.header().string("Content-Type", "text/plain"))
                .andExpect(MockMvcResultMatchers.header().string("Content-Disposition", "inline; filename=\"helloword.txt\""))
                .andExpect(MockMvcResultMatchers.header().string("Expires", new BaseMatcher<String>() {
                    @Override
                    public boolean matches(Object item) {                        
                        return ((String) item).matches(LocalDateTime.now(ZoneId.of("UTC")).plusDays(365).format(formatter));
                    }

                    @Override
                    public void describeTo(Description description) {
                    }

                }))
                .andExpect(MockMvcResultMatchers.header().string("Cache-Control", "max-age=31536000, public"));

        verify(this.gridFsTemplate).findOne(any(Query.class));
        verifyNoMoreInteractions(this.gridFsTemplate);
    }
}
