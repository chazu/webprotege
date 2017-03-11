package edu.stanford.bmir.protege.web.server.issues;

import edu.stanford.bmir.protege.web.server.filemanager.FileContentsCache;
import edu.stanford.bmir.protege.web.server.inject.ApplicationName;
import edu.stanford.bmir.protege.web.server.logging.WebProtegeLogger;
import edu.stanford.bmir.protege.web.server.place.PlaceUrl;
import edu.stanford.bmir.protege.web.server.templates.TemplateEngine;
import edu.stanford.bmir.protege.web.shared.issues.Comment;
import edu.stanford.bmir.protege.web.shared.issues.EntityDiscussionThread;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Matthew Horridge
 * Stanford Center for Biomedical Informatics Research
 * 10 Mar 2017
 */
public class CommentNotificationEmailGenerator {

    public static final String PROJECT_DISPLAY_NAME = "project.displayName";

    public static final String PROJECT_URL = "project.url";

    public static final String ENTITY_URL = "entity.url";

    public static final String THREAD = "thread";

    public static final String COMMENT = "comment";

    public static final String APPLICATION_NAME = "application.name";


    private final TemplateEngine templateEngine;

    private final FileContentsCache templateFile;

    private final WebProtegeLogger webProtegeLogger;

    private final PlaceUrl placeUrl;

    private final String applicationName;

    @Inject
    public CommentNotificationEmailGenerator(@Nonnull @CommentNotificationTemplate FileContentsCache templateFile,
                                             @Nonnull TemplateEngine templateEngine,
                                             @Nonnull @ApplicationName String applicationName,
                                             @Nonnull PlaceUrl placeUrl,
                                             @Nonnull WebProtegeLogger webProtegeLogger) {
        this.templateEngine = templateEngine;
        this.templateFile = templateFile;
        this.webProtegeLogger = webProtegeLogger;
        this.placeUrl = placeUrl;
        this.applicationName = applicationName;
    }

    @Nonnull
    public String generateEmailBody(@Nonnull String projectDisplayName,
                           @Nonnull EntityDiscussionThread thread,
                           @Nonnull Comment comment) {
        try {
            Map<String, Object> objects = new HashMap<>();
            objects.put(APPLICATION_NAME, applicationName);
            objects.put(PROJECT_DISPLAY_NAME, projectDisplayName);
            objects.put(PROJECT_URL, placeUrl.getProjectUrl(thread.getProjectId()));
            objects.put(ENTITY_URL, placeUrl.getEntityUrl(thread.getProjectId(), thread.getEntity()));
            objects.put(THREAD, thread);
            objects.put(COMMENT, comment);
            String template = templateFile.getContents();
            return templateEngine.populateTemplate(template, objects);
        } catch (Exception e) {
            webProtegeLogger.severe(e);
            return String.format("Invalid template file: %s (Cause: %s)",
                                 templateFile.getFile().getName(),
                                 e.getMessage());
        }
    }
}
