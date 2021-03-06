package edu.stanford.bmir.protege.web.client.change;

import com.google.common.collect.Ordering;
import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.shared.DateTimeFormat;
import com.google.gwt.safehtml.shared.SafeHtml;
import edu.stanford.bmir.protege.web.client.Messages;
import edu.stanford.bmir.protege.web.client.dispatch.DispatchServiceCallback;
import edu.stanford.bmir.protege.web.client.dispatch.DispatchServiceManager;
import edu.stanford.bmir.protege.web.client.download.ProjectRevisionDownloader;
import edu.stanford.bmir.protege.web.client.library.dlg.DialogButton;
import edu.stanford.bmir.protege.web.client.library.msgbox.MessageBox;
import edu.stanford.bmir.protege.web.client.pagination.HasPagination;
import edu.stanford.bmir.protege.web.client.permissions.LoggedInUserProjectPermissionChecker;
import edu.stanford.bmir.protege.web.client.progress.HasBusy;
import edu.stanford.bmir.protege.web.shared.TimeUtil;
import edu.stanford.bmir.protege.web.shared.change.*;
import edu.stanford.bmir.protege.web.shared.diff.DiffElement;
import edu.stanford.bmir.protege.web.shared.download.DownloadFormatExtension;
import edu.stanford.bmir.protege.web.shared.pagination.Page;
import edu.stanford.bmir.protege.web.shared.pagination.PageRequest;
import edu.stanford.bmir.protege.web.shared.project.ProjectId;
import edu.stanford.bmir.protege.web.shared.revision.RevisionNumber;
import edu.stanford.bmir.protege.web.shared.user.UserId;
import org.semanticweb.owlapi.model.OWLEntity;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static edu.stanford.bmir.protege.web.client.library.dlg.DialogButton.CANCEL;
import static edu.stanford.bmir.protege.web.shared.access.BuiltInAction.REVERT_CHANGES;
import static edu.stanford.bmir.protege.web.shared.access.BuiltInAction.VIEW_CHANGES;

/**
 * Matthew Horridge Stanford Center for Biomedical Informatics Research 26/02/15
 */
public class ChangeListViewPresenter {


    private final ChangeListView view;

    private final DispatchServiceManager dispatchServiceManager;

    private final LoggedInUserProjectPermissionChecker permissionChecker;

    private final Messages messages;

    private boolean revertChangesVisible = false;

    private boolean downloadVisible = false;

    private Optional<ProjectId> projectId = Optional.empty();

    private HasBusy hasBusy = busy -> {
    };

    private Optional<GetProjectChangesAction> lastAction = Optional.empty();

    private HasPagination.PageNumberChangedHandler pageNumberChangedHandler = pageNumber -> {};

    private MessageBox messageBox;

    @Inject
    public ChangeListViewPresenter(ChangeListView view,
                                   DispatchServiceManager dispatchServiceManager,
                                   LoggedInUserProjectPermissionChecker permissionChecker,
                                   Messages messages, MessageBox messageBox) {
        this.view = view;
        this.permissionChecker = permissionChecker;
        this.dispatchServiceManager = dispatchServiceManager;
        this.messages = messages;
        this.messageBox = messageBox;
        this.view.setPageNumberChangedHandler(pageNumber -> pageNumberChangedHandler.handlePageNumberChanged(pageNumber));
    }

    public void setRevertChangesVisible(boolean revertChangesVisible) {
        this.revertChangesVisible = revertChangesVisible;
    }

    public void setDownloadVisible(boolean downloadVisible) {
        this.downloadVisible = downloadVisible;
    }

    public ChangeListView getView() {
        return view;
    }

    public void setHasBusy(@Nonnull HasBusy hasBusy) {
        this.hasBusy = checkNotNull(hasBusy);
    }

    public void setChangesForProject(ProjectId projectId) {
        this.projectId = Optional.of(projectId);
        this.pageNumberChangedHandler = pageNumber -> setChangesForProject(projectId);
        view.clear();
        PageRequest pageRequest = PageRequest.requestPage(view.getPageNumber());
        GetProjectChangesAction action = new GetProjectChangesAction(projectId, Optional.empty(), pageRequest);
        lastAction = Optional.of(action);
        SubjectDisplay displaySubject = SubjectDisplay.DISPLAY_SUBJECT;
        dispatchServiceManager.execute(action,
                                       hasBusy,
                                       result -> fillView(result.getChanges(),
                                                          displaySubject,
                                                          revertChangesVisible,
                                                          downloadVisible));
    }

    public void setChangesForEntity(ProjectId projectId, OWLEntity entity) {
        this.projectId = Optional.of(projectId);
        this.pageNumberChangedHandler = pageNumber -> setChangesForEntity(projectId, entity);
        view.clear();
        PageRequest pageRequest = PageRequest.requestPage(view.getPageNumber());
        GetProjectChangesAction action = new GetProjectChangesAction(projectId, Optional.of(entity), pageRequest);
        SubjectDisplay doNotDisplaySubject = SubjectDisplay.DO_NOT_DISPLAY_SUBJECT;
        dispatchServiceManager.execute(action,
                                       hasBusy,
                                       result -> fillView(result.getChanges(),
                                                          doNotDisplaySubject,
                                                          revertChangesVisible,
                                                          downloadVisible));
    }

    public void setChangesForWatches(ProjectId projectId, UserId userId) {
        this.projectId = Optional.of(projectId);
        this.pageNumberChangedHandler = pageNumber -> setChangesForWatches(projectId, userId);
        view.clear();
        GetWatchedEntityChangesAction action = new GetWatchedEntityChangesAction(projectId, userId);
        SubjectDisplay displaySubject = SubjectDisplay.DISPLAY_SUBJECT;
        dispatchServiceManager.execute(action,
                                       result -> fillView(result.getChanges(),
                                                          displaySubject,
                                                          revertChangesVisible,
                                                          downloadVisible));
    }

    public void clear() {
        view.clear();
    }

    private void fillView(Page<ProjectChange> changes,
                          SubjectDisplay subjectDisplay,
                          boolean revertChangesVisible,
                          boolean downloadVisible) {
        view.clear();
        permissionChecker.hasPermission(VIEW_CHANGES,
                                        viewChanges -> {
                                            if (viewChanges) {
                                                insertChangesIntoView(changes,
                                                                      subjectDisplay,
                                                                      revertChangesVisible,
                                                                      downloadVisible);
                                            }
                                        });
    }

    private void insertChangesIntoView(Page<ProjectChange> changes,
                                       SubjectDisplay subjectDisplay,
                                       boolean revertChangesVisible, boolean downloadVisible) {
        List<ProjectChange> projectChanges = new ArrayList<>(changes.getPageElements());
        Collections.sort(projectChanges, Ordering.compound(Collections.singletonList(
                Ordering.from(new ProjectChangeTimestampComparator()).reverse())));
        long previousTimeStamp = 0;
        view.setPageCount(changes.getPageCount());
        view.setPageNumber(changes.getPageNumber());
        for (final ProjectChange projectChange : projectChanges) {
            long changeTimeStamp = projectChange.getTimestamp();
            if (!TimeUtil.isSameCalendarDay(previousTimeStamp, changeTimeStamp)) {
                previousTimeStamp = changeTimeStamp;
                Date date = new Date(changeTimeStamp);
                view.addSeparator("\u25C9   " + messages.change_changesOn() + " " + DateTimeFormat.getFormat("EEE, d MMM yyyy").format(date));
            }

            ChangeDetailsView view = new ChangeDetailsViewImpl();
            if (subjectDisplay == SubjectDisplay.DISPLAY_SUBJECT) {
//                List<OWLEntityData> subjects = new ArrayList<>(projectChange.getSubjects());
//                Collections.sort(subjects, OWLEntityData::compareToIgnoreCase);
//                view.setSubjects(subjects);
            }
            view.setRevision(projectChange.getRevisionNumber());
            view.setAuthor(projectChange.getAuthor());
            view.setHighLevelDescription(projectChange.getSummary());
            view.setRevertRevisionVisible(false);
            if (revertChangesVisible) {
                permissionChecker.hasPermission(REVERT_CHANGES,
                                                view::setRevertRevisionVisible);
            }
            view.setRevertRevisionHandler(revisionNumber -> ChangeListViewPresenter.this.handleRevertRevision(
                    projectChange));
            view.setDownloadRevisionHandler(revisionNumber -> {
                ProjectRevisionDownloader downloader = new ProjectRevisionDownloader(
                        projectId.get(),
                        revisionNumber,
                        DownloadFormatExtension.owl);
                downloader.download();
            });
            view.setDownloadRevisionVisible(downloadVisible);
            Page<DiffElement<String, SafeHtml>> page = projectChange.getDiff();
            List<DiffElement<String, SafeHtml>> pageElements = page.getPageElements();
            view.setDiff(pageElements, (int) page.getTotalElements());
            view.setChangeCount(projectChange.getChangeCount());
            view.setTimestamp(changeTimeStamp);
            this.view.addChangeDetailsView(view);
        }
    }

    private void handleRevertRevision(final ProjectChange projectChange) {
        startRevertChangesWorkflow(projectChange);
    }

    private void startRevertChangesWorkflow(final ProjectChange projectChange) {
        String subMessage = messages.change_revertChangesInRevisionQuestion();
        messageBox.showConfirmBox(
                messages.change_revertChangesQuestion(),
                subMessage,
                CANCEL,
                DialogButton.get(messages.change_revert()),
                () -> revertChanges(projectChange),
                CANCEL);
    }

    private void revertChanges(ProjectChange projectChange) {
        GWT.log("Reverting revision " + projectChange.getRevisionNumber().getValue());
        projectId.ifPresent(theProjectId -> {
            final RevisionNumber revisionNumber = projectChange.getRevisionNumber();
            dispatchServiceManager.execute(new RevertRevisionAction(theProjectId, revisionNumber),
                                           result -> {
                                               messageBox.showMessage("Changes in revision " + revisionNumber.getValue() + " have been reverted");
                                               lastAction.ifPresent(action -> setChangesForProject(action.getProjectId()));
                                           });
        });
    }

}
