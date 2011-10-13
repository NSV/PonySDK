/*
 * Copyright (c) 2011 PonySDK
 *  Owners:
 *  Luciano Broussal  <luciano.broussal AT gmail.com>
 *	Mathieu Barbier   <mathieu.barbier AT gmail.com>
 *	Nicolas Ciaravola <nicolas.ciaravola.pro AT gmail.com>
 *  
 *  WebSite:
 *  http://code.google.com/p/pony-sdk/
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.ponysdk.ui.server.list;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.ponysdk.core.activity.AbstractActivity;
import com.ponysdk.core.command.Command;
import com.ponysdk.core.event.EventBus;
import com.ponysdk.core.event.EventBusAware;
import com.ponysdk.core.event.SimpleEventBus;
import com.ponysdk.core.export.ExportContext;
import com.ponysdk.core.export.ExportContext.ExportType;
import com.ponysdk.core.query.CriterionField;
import com.ponysdk.core.query.Query;
import com.ponysdk.core.query.Query.QueryMode;
import com.ponysdk.core.query.Result;
import com.ponysdk.core.query.SortingType;
import com.ponysdk.ui.server.addon.PDialogBox;
import com.ponysdk.ui.server.addon.PNotificationManager;
import com.ponysdk.ui.server.addon.PNotificationManager.Notification;
import com.ponysdk.ui.server.basic.IsPWidget;
import com.ponysdk.ui.server.basic.PAcceptsOneWidget;
import com.ponysdk.ui.server.basic.PAnchor;
import com.ponysdk.ui.server.basic.PCheckBox;
import com.ponysdk.ui.server.basic.PCommand;
import com.ponysdk.ui.server.basic.PConfirmDialog;
import com.ponysdk.ui.server.basic.PConfirmDialogHandler;
import com.ponysdk.ui.server.basic.PHTML;
import com.ponysdk.ui.server.basic.PHorizontalPanel;
import com.ponysdk.ui.server.basic.PLabel;
import com.ponysdk.ui.server.basic.PMenuBar;
import com.ponysdk.ui.server.basic.PMenuItem;
import com.ponysdk.ui.server.basic.PPanel;
import com.ponysdk.ui.server.basic.PSimplePanel;
import com.ponysdk.ui.server.basic.PVerticalPanel;
import com.ponysdk.ui.server.basic.event.PClickEvent;
import com.ponysdk.ui.server.basic.event.PClickHandler;
import com.ponysdk.ui.server.basic.event.PValueChangeHandler;
import com.ponysdk.ui.server.form.DefaultFormView;
import com.ponysdk.ui.server.form.FormActivity;
import com.ponysdk.ui.server.form.FormConfiguration;
import com.ponysdk.ui.server.form.FormField;
import com.ponysdk.ui.server.form.FormView;
import com.ponysdk.ui.server.list.event.AddCustomColumnDescriptorEvent;
import com.ponysdk.ui.server.list.event.AddCustomColumnDescriptorHandler;
import com.ponysdk.ui.server.list.event.RefreshListEvent;
import com.ponysdk.ui.server.list.event.RefreshListHandler;
import com.ponysdk.ui.server.list.event.RemoveColumnDescriptorEvent;
import com.ponysdk.ui.server.list.event.RemoveColumnDescriptorHandler;
import com.ponysdk.ui.server.list.event.ShowSubListEvent;
import com.ponysdk.ui.server.list.event.ShowSubListHandler;
import com.ponysdk.ui.server.list.event.SortColumnEvent;
import com.ponysdk.ui.server.list.event.SortColumnHandler;
import com.ponysdk.ui.server.list.form.AddCustomColumnDescriptorForm;
import com.ponysdk.ui.server.list.paging.MenuBarPagingView;
import com.ponysdk.ui.server.list.paging.PagingActivity;
import com.ponysdk.ui.server.list.paging.PagingView;
import com.ponysdk.ui.server.list.paging.event.PagingSelectionChangeEvent;
import com.ponysdk.ui.server.list.paging.event.PagingSelectionChangeHandler;
import com.ponysdk.ui.server.list.renderer.CellRenderer;
import com.ponysdk.ui.server.list.renderer.ComplexHeaderCellRenderer;
import com.ponysdk.ui.server.list.renderer.EmptyCellRenderer;
import com.ponysdk.ui.server.list.renderer.HeaderCellRenderer;
import com.ponysdk.ui.server.list.valueprovider.BeanValueProvider;
import com.ponysdk.ui.server.list.valueprovider.BooleanValueProvider;
import com.ponysdk.ui.server.list.valueprovider.ValueProvider;
import com.ponysdk.ui.terminal.basic.PHorizontalAlignment;

public class ComplexListActivity<D> extends AbstractActivity implements PagingSelectionChangeHandler, SortColumnHandler, RefreshListHandler, ShowSubListHandler<D>,
        AddCustomColumnDescriptorHandler<D>, RemoveColumnDescriptorHandler {

    private SimpleListActivity<D> simpleListActivity;

    protected final ComplexListView complexListView;
    protected ComplexListCommandFactory<D> commandFactory;
    protected final List<ListColumnDescriptor<D, ?>> listColumnDescriptors;
    protected Map<String, FormField> formFieldsByPojoProperty = new HashMap<String, FormField>();
    protected Map<String, CriterionField> criterionFieldsByPojoProperty = new HashMap<String, CriterionField>();
    private static SimpleDateFormat formater = new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss");

    private PagingActivity pagingActivity;
    protected final ComplexListConfiguration<D> complexListConfiguration;

    private FormActivity searchFormActivity;

    private SortingType currentSortingType = SortingType.NONE;
    private String currentSortingPojoPropertyKey;

    protected int currentPage;
    protected List<PRowCheckBox> rowSelectors = new ArrayList<PRowCheckBox>();
    protected PCheckBox mainCheckBox;
    protected boolean mainSelectorAction;
    protected boolean rowSelectorAction;

    private final EventBus localEventBus;
    private EventBus eventBus; // use to forward ShowSubListEvent

    private PMenuItem refreshButton;
    private PMenuItem resetButton;

    private String debugID;

    private Result<List<D>> findResult;

    SelectionMode selectionMode = SelectionMode.NONE;

    private PMenuItem addCustomColumnButton;

    public ComplexListActivity(ComplexListConfiguration<D> complexListConfiguration, ComplexListView complexListView) {
        this(complexListConfiguration, complexListView, null);
    }

    public ComplexListActivity(ComplexListConfiguration<D> complexListConfiguration, ComplexListView complexListView, EventBus eventBus) {
        this.eventBus = eventBus;
        this.complexListConfiguration = complexListConfiguration;
        this.listColumnDescriptors = complexListConfiguration.getColumnDescriptors();

        if (complexListConfiguration.isSelectionColumnEnabled()) {
            listColumnDescriptors.add(0, getSelectableRow());
        }
        if (complexListConfiguration.isShowSubListColumnEnabled()) {
            listColumnDescriptors.add(1, getShowSubListRow());
        }

        this.complexListView = complexListView;

        this.localEventBus = new SimpleEventBus();
        this.localEventBus.addHandler(PagingSelectionChangeEvent.TYPE, this);
        this.localEventBus.addHandler(SortColumnEvent.TYPE, this);
        this.localEventBus.addHandler(RefreshListEvent.TYPE, this);
        this.localEventBus.addHandler(ShowSubListEvent.TYPE, this);
        this.localEventBus.addHandler(AddCustomColumnDescriptorEvent.TYPE, this);
        this.localEventBus.addHandler(RemoveColumnDescriptorEvent.TYPE, this);

        for (final ListColumnDescriptor<D, ?> columnDescriptor : listColumnDescriptors) {
            if (columnDescriptor.getHeaderCellRenderer() instanceof EventBusAware) {
                ((EventBusAware) columnDescriptor.getHeaderCellRenderer()).setEventBus(localEventBus);
            }
            if (columnDescriptor.getCellRenderer() instanceof EventBusAware) {
                ((EventBusAware) columnDescriptor.getCellRenderer()).setEventBus(localEventBus);
            }
        }

    }

    private class PRowCheckBox extends PCheckBox {
        private int row;
        private int datasize;

        public void setRow(int row) {
            this.row = row;
        }

        public int getRow() {
            return row;
        }

        public void setDatasize(int datasize) {
            this.datasize = datasize;
        }

        public int getDatasize() {
            return datasize;
        }

        @SuppressWarnings("unchecked")
        @Override
        public D getData() {
            return (D) data;
        }

    }

    private ListColumnDescriptor<D, Boolean> getSelectableRow() {
        final ListColumnDescriptor<D, Boolean> listColumnDescriptor = new ListColumnDescriptor<D, Boolean>();
        listColumnDescriptor.setValueProvider(new BooleanValueProvider<D>(false));
        listColumnDescriptor.setSubCellRenderer(new EmptyCellRenderer<D, Boolean>());
        listColumnDescriptor.setCellRenderer(new CellRenderer<D, Boolean>() {

            @Override
            public IsPWidget render(final int row, D data, Boolean value) {
                final PRowCheckBox checkBox = new PRowCheckBox();
                checkBox.setRow(row);

                checkBox.addValueChangeHandler(new PValueChangeHandler<Boolean>() {

                    @Override
                    public void onValueChange(Boolean value) {
                        if (value) {
                            if (checkBox.getRow() != 0) {
                                simpleListActivity.selectRow(checkBox.getRow());
                                selectionMode = SelectionMode.PARTIAL;
                            }
                            if (!mainSelectorAction) {
                                boolean allChecked = true;
                                for (final PCheckBox box : rowSelectors) {
                                    if (!box.equals(checkBox)) {
                                        if (!box.getValue()) {
                                            allChecked = false;
                                            break;
                                        }
                                    }
                                }
                                if (allChecked) {
                                    rowSelectorAction = true;
                                    mainCheckBox.setValue(true);
                                    showSelectAllOption();
                                    rowSelectorAction = false;
                                }
                            }
                        } else {
                            if (checkBox.getRow() != 0)
                                simpleListActivity.unSelectRow(checkBox.getRow());
                            hideSelectAllOption();
                            if (rowSelectors.isEmpty()) {
                                selectionMode = SelectionMode.NONE;
                            } else {
                                selectionMode = SelectionMode.PARTIAL;
                            }
                            if (!mainSelectorAction) {
                                rowSelectorAction = true;
                                mainCheckBox.setValue(false);
                                rowSelectorAction = false;
                            }
                        }
                    }
                });

                rowSelectors.add(checkBox);

                return checkBox;
            }
        });

        listColumnDescriptor.setHeaderCellRenderer(new HeaderCellRenderer() {

            @Override
            public IsPWidget render() {
                mainCheckBox = new PCheckBox();
                mainCheckBox.addValueChangeHandler(new PValueChangeHandler<Boolean>() {

                    @Override
                    public void onValueChange(Boolean value) {
                        triggerMainCheckBoxValueChange(value);
                    }

                });

                return mainCheckBox;
            }

            @Override
            public String getCaption() {
                return null;
            }
        });

        return listColumnDescriptor;
    }

    private void triggerMainCheckBoxValueChange(Boolean value) {
        if (!rowSelectorAction) {
            mainSelectorAction = true;
            mainCheckboxChanged(value);
            mainSelectorAction = false;
        }
    }

    private void mainCheckboxChanged(Boolean value) {
        changeRowSelectorsState(value);
        if (value) {
            showSelectAllOption();
            selectionMode = SelectionMode.PARTIAL;
        } else {
            hideSelectAllOption();
            selectionMode = SelectionMode.NONE;
        }

        // rowSelectorAction = true;
        mainCheckBox.setValue(value);
        // rowSelectorAction = false;
    }

    private void showSelectAllOption() {
        final PHorizontalPanel panel = new PHorizontalPanel();
        panel.setHorizontalAlignment(PHorizontalAlignment.ALIGN_CENTER);
        panel.setStyleName("pony-ComplexList-OptionSelectionPanel");
        final int dataSelectedCount = getSelectedData().getSelectedData().size();
        final PLabel label = new PLabel("All " + dataSelectedCount + " items on this page are selected.");
        final int fullSize = findResult.getFullSize();
        panel.add(label);
        if (fullSize > complexListConfiguration.getPageSize()) {
            final PAnchor anchor = new PAnchor("Select all " + fullSize + " final items in Inbox");
            anchor.addClickHandler(new PClickHandler() {

                @Override
                public void onClick(PClickEvent event) {
                    selectionMode = SelectionMode.FULL;
                    showClearSelectAllOption();
                }
            });
            panel.add(anchor);
            panel.setCellHorizontalAlignment(label, PHorizontalAlignment.ALIGN_RIGHT);
            panel.setCellHorizontalAlignment(anchor, PHorizontalAlignment.ALIGN_LEFT);
        }
        complexListView.getTopListLayout().setWidget(panel);
    }

    private void showClearSelectAllOption() {
        final PHorizontalPanel panel = new PHorizontalPanel();
        panel.setHorizontalAlignment(PHorizontalAlignment.ALIGN_CENTER);
        panel.setStyleName("pony-ComplexList-OptionSelectionPanel");
        final PLabel label = new PLabel("All " + findResult.getFullSize() + " items are selected.");
        final PAnchor anchor = new PAnchor("Clear selection");
        anchor.addClickHandler(new PClickHandler() {

            @Override
            public void onClick(PClickEvent event) {
                mainCheckboxChanged(false);
            }
        });
        panel.add(label);
        panel.add(anchor);
        panel.setCellHorizontalAlignment(label, PHorizontalAlignment.ALIGN_RIGHT);
        panel.setCellHorizontalAlignment(anchor, PHorizontalAlignment.ALIGN_LEFT);
        complexListView.getTopListLayout().setWidget(panel);
    }

    private void hideSelectAllOption() {
        complexListView.getTopListLayout().setWidget(new PHTML());
    }

    private ListColumnDescriptor<D, String> getShowSubListRow() {

        final ListColumnDescriptor<D, String> listColumnDescriptor = new ListColumnDescriptor<D, String>();
        listColumnDescriptor.setValueProvider(new ValueProvider<D, String>() {

            @Override
            public String getValue(D data) {
                return null;
            }
        });
        listColumnDescriptor.setCellRenderer(new DetailsCellRenderer<D, String>());
        listColumnDescriptor.setSubCellRenderer(new EmptyCellRenderer<D, String>());
        listColumnDescriptor.setHeaderCellRenderer(new HeaderCellRenderer() {

            @Override
            public IsPWidget render() {
                return new PLabel("Details");
            }

            @Override
            public String getCaption() {
                return "Details";
            }
        });

        return listColumnDescriptor;
    }

    private void buildSearchForm() {
        if (complexListConfiguration.isEnableForm()) {
            PPanel formLayout = complexListConfiguration.getFormLayout();
            if (formLayout == null) {
                formLayout = new PVerticalPanel();
            }

            final FormView formView = new DefaultFormView("SearchForm", formLayout);
            final FormConfiguration formConfiguration = new FormConfiguration();
            formConfiguration.setName(complexListConfiguration.getTableName() + "filterForm");

            searchFormActivity = new FormActivity(formConfiguration, formView);
            searchFormActivity.start(complexListView.getFormLayout());
        }
    }

    private void buildPaging() {
        final PagingView pagingView = new MenuBarPagingView();
        pagingActivity = new PagingActivity(pagingView);
        pagingActivity.setPageSize(complexListConfiguration.getPageSize());
        pagingActivity.setEventBus(localEventBus);
        pagingActivity.start(complexListView.getPagingLayout());
    }

    private void buildActions() {
        final PMenuBar actionBar = new PMenuBar();
        actionBar.setStyleName("pony-ActionToolbar");

        refreshButton = new PMenuItem("Refresh", new PCommand() {

            @Override
            public void execute() {
                refresh();
            }
        });
        actionBar.addItem(refreshButton);
        actionBar.addSeparator();

        resetButton = new PMenuItem("Reset", new PCommand() {

            @Override
            public void execute() {
                reset();
            }
        });

        actionBar.addItem(resetButton);
        if (complexListConfiguration.isCustomColumnEnabled()) {
            addCustomColumnButton = new PMenuItem("Add Custom", new PCommand() {

                @Override
                public void execute() {
                    localEventBus.fireEvent(new AddCustomColumnDescriptorEvent<D>(this));
                }
            });
            actionBar.addSeparator();
            actionBar.addItem(addCustomColumnButton);
        }

        if (complexListConfiguration.getExportConfiguration() != null) {
            actionBar.addSeparator();
            final PMenuBar exportListMenuBar = new PMenuBar(true);

            for (final ExportType exportType : complexListConfiguration.getExportConfiguration().getExportTypes()) {
                final PMenuItem item = new PMenuItem(exportType.name(), new PCommand() {

                    @Override
                    public void execute() {

                        final SelectionResult<D> selectionResult = getSelectedData();
                        if (selectionResult.getSelectedData() != null && selectionResult.getSelectedData().isEmpty()) {
                            PNotificationManager.notify("Export failed", "Please select data to export", Notification.WARNING_MESSAGE);
                            return;
                        }
                        final Query query = createQuery(currentPage);
                        if (SelectionMode.FULL.equals(selectionMode)) {
                            query.setQueryMode(QueryMode.FULL_RESULT);
                        }
                        final ExportContext<D> exportContext = new ExportContext<D>(query, complexListConfiguration.getExportConfiguration().getExportableFields(), selectionResult);
                        exportContext.setType(exportType);
                        final Command command = commandFactory.newExportCommand(ComplexListActivity.this, exportContext);
                        command.execute();
                    }
                });
                exportListMenuBar.addItem(item);
            }
            actionBar.addItem("Export", exportListMenuBar);

        }

        complexListView.getToolbarLayout().add(actionBar);
        complexListView.getToolbarLayout().addSepararator();
    }

    public void setData(Result<List<D>> result) {
        findResult = result;
        rowSelectors.clear();

        simpleListActivity.setData(result.getData());

        int i = 0;
        for (final D data : result.getData()) {
            rowSelectors.get(i).setData(data);
            i = i + 1;
        }

        getComplexListView().addHeaderStyle("pony-ComplexList-ColumnHeader");
        final float executionTime = result.getExecutionTime() * 0.000000001f;// TO
        complexListView.setSearchResultInformation("found " + result.getData().size() + " records (" + executionTime + " secondes), last refresh at "
                + formater.format(Calendar.getInstance().getTime()));
        pagingActivity.process(result.getFullSize());
    }

    public Result<List<D>> getData() {
        return findResult;
    }

    public void addCustomDescriptor(ListColumnDescriptor<D, ?> customDescriptor) {
        simpleListActivity.addCustomDescriptor(customDescriptor);
    }

    public void insertSubList(int row, List<D> datas) {
        simpleListActivity.insertSubList(row, datas);
        for (final PRowCheckBox c : rowSelectors) {
            if (c.getRow() == row) {
                c.setDatasize(datas.size());
            }
            if (c.getRow() > row) {
                c.setRow(c.getRow() + datas.size());
            }
        }
    }

    public void removeSubList(int fatherRow) {
        simpleListActivity.removeSubList(fatherRow);
        int dataSize = 0;
        for (final PRowCheckBox c : rowSelectors) {
            if (c.getRow() == fatherRow) {
                dataSize = c.getDatasize();
                c.setDatasize(0);
            }
            if (c.getRow() > fatherRow) {
                c.setRow(c.getRow() - dataSize);
            }
        }
    }

    public void setCommandFactory(ComplexListCommandFactory<D> commandFactory) {
        this.commandFactory = commandFactory;
    }

    protected void changeRowSelectorsState(final boolean selected) {
        for (final PRowCheckBox checkBox : rowSelectors) {
            simpleListActivity.selectRow(checkBox.getRow());
            if (selected) {
                selectRow(checkBox.getRow());
            } else {
                unSelectRow(checkBox.getRow());
            }
        }
    }

    public void reset() {
        for (final FormField formField : formFieldsByPojoProperty.values()) {
            formField.reset();
        }

        if (complexListConfiguration.isSelectionColumnEnabled()) {
            changeRowSelectorsState(false);
            rowSelectors.clear();
            mainCheckBox.setValue(false);
            triggerMainCheckBoxValueChange(false);
        }

        currentSortingPojoPropertyKey = null;
        currentSortingType = SortingType.NONE;
        findResult = null;
    }

    public void refresh() {
        pagingActivity.clear();
        refresh(0);
    }

    public void refresh(int page) {
        if (complexListConfiguration.isSearchFormMustBeValid()) {
            if (!searchFormActivity.isValid()) {
                return;
            }
        }

        if (complexListConfiguration.isSelectionColumnEnabled()) {
            mainCheckBox.setValue(false);
            triggerMainCheckBoxValueChange(false);
        }

        final Query query = createQuery(page);
        final Command command = commandFactory.newFindCommand(ComplexListActivity.this, query);
        if (command == null) {
            throw new IllegalStateException("FindCommand of the complex list can't be null");
        }
        command.execute();
        complexListView.updateView();

    }

    protected Query createQuery(int page) {
        final List<CriterionField> criteria = new ArrayList<CriterionField>();
        for (final Entry<String, FormField> entry : formFieldsByPojoProperty.entrySet()) {
            final FormField formField = entry.getValue();

            if (formField.getValue() != null) {
                final CriterionField criterionField = criterionFieldsByPojoProperty.get(entry.getKey());
                criterionField.setValue(formField.getValue());
                criteria.add(criterionField);
            }
        }

        if (currentSortingPojoPropertyKey != null) {
            final CriterionField criterionField = new CriterionField(currentSortingPojoPropertyKey);
            criterionField.setSortingType(currentSortingType);
            criteria.add(criterionField);
        }

        final Query query = new Query();
        query.setCriteria(criteria);
        query.setPageNum(page);
        query.setPageSize(complexListConfiguration.getPageSize());
        return query;
    }

    public FormActivity getForm() {
        return searchFormActivity;
    }

    @Override
    public void onPageChange(PagingSelectionChangeEvent event) {
        if (event.getSource().equals(pagingActivity)) {
            currentPage = event.getPage();
            refresh(currentPage);
        }
    }

    public void registerSearchCriteria(final CriterionField criterionField, final FormField formField) {
        formFieldsByPojoProperty.put(criterionField.getPojoProperty(), formField);
        criterionFieldsByPojoProperty.put(criterionField.getPojoProperty(), criterionField);
    }

    @Override
    public void onColumnSort(SortColumnEvent event) {
        currentSortingType = event.getSortingType();
        currentSortingPojoPropertyKey = event.getPojoPropertyKey();
        refresh();
    }

    public SelectionResult<D> getSelectedData() {
        if (!complexListConfiguration.isSelectionColumnEnabled()) {
            return new SelectionResult<D>(SelectionMode.FULL, new ArrayList<D>());
        }

        final List<D> data = simpleListActivity.getData();
        final List<D> selectedData = new ArrayList<D>();

        for (int i = 0; i < rowSelectors.size(); i++) {
            final PCheckBox rowSelector = rowSelectors.get(i);
            if (rowSelector.getValue()) {
                selectedData.add(data.get(i));
            }
        }

        final SelectionResult<D> selectionResult = new SelectionResult<D>(selectionMode, selectedData);
        return selectionResult;
    }

    public void setSelectedData(List<D> data) {
        // unselect previously selected data
        if (mainCheckBox != null) {
            mainCheckBox.setValue(false);
            triggerMainCheckBoxValueChange(false);
        }

        // select
        for (final D d : data) {
            selectRow(getRow(d));
        }
    }

    public int getRow(D data) {
        if (findResult == null)
            return -1;

        for (final PRowCheckBox checkBox : rowSelectors) {
            if (data.equals(checkBox.getData())) {
                return checkBox.getRow();
            }
        }

        return -1;
    }

    @Override
    public void onRefreshList(final RefreshListEvent event) {
        refresh();
    }

    public void selectRow(int row) {
        if (complexListConfiguration.isSelectionColumnEnabled()) {
            selectRowCheckBox(row);
        }
        this.simpleListActivity.selectRow(row);
    }

    public void unSelectRow(int row) {
        if (complexListConfiguration.isSelectionColumnEnabled()) {
            unselectRowCheckBox(row);
        }
        this.simpleListActivity.unSelectRow(row);
    }

    private void selectRowCheckBox(int row) {
        for (final PRowCheckBox checkBox : rowSelectors) {
            if (checkBox.getRow() == row) {
                checkBox.setValue(true);
            }
        }
    }

    private void unselectRowCheckBox(int row) {
        for (final PRowCheckBox checkBox : rowSelectors) {
            if (checkBox.getRow() == row) {
                checkBox.setValue(false);
            }
        }
    }

    @Override
    public void start(PAcceptsOneWidget world) {
        this.simpleListActivity = new SimpleListActivity<D>(complexListConfiguration.getTableName(), complexListView, listColumnDescriptors, localEventBus);
        buildSearchForm();
        buildActions();
        buildPaging();
        world.setWidget(complexListView);

        if (debugID != null) {
            ensureDebugId(debugID);
        }
    }

    @Override
    public void onShowSubList(ShowSubListEvent<D> event) {
        eventBus.fireEvent(new ShowSubListEvent<D>(this, event.getData(), event.isShow(), event.getRow()));
    }

    public ComplexListView getComplexListView() {
        return complexListView;
    }

    public void ensureDebugId(String debugID) {
        this.debugID = debugID;
        if (refreshButton != null) {
            refreshButton.ensureDebugId(debugID + "[refresh]");
        }
        if (resetButton != null) {
            resetButton.ensureDebugId(debugID + "[reset]");
        }

        if (simpleListActivity != null) {
            simpleListActivity.ensureDebugId(debugID);
        }
    }

    @Override
    public void onAddCustomColumnDescriptor(AddCustomColumnDescriptorEvent<D> event) {
        final FormView formView = new DefaultFormView("AddCustomColumnDescriptorForm");
        final FormConfiguration formConfiguration = new FormConfiguration();
        formConfiguration.setName("Form");
        final AddCustomColumnDescriptorForm<D> form = new AddCustomColumnDescriptorForm<D>(formConfiguration, formView, complexListConfiguration.getClas());
        final PSimplePanel windowContent = new PSimplePanel();
        form.start(windowContent);
        PConfirmDialog.show("Add custom column", windowContent, "Ok", "Cancel", new PConfirmDialogHandler() {

            @Override
            public boolean onOK(PDialogBox dialogBox) {
                if (form.isValid()) {
                    final ListColumnDescriptor<D, Object> columnDescriptor = new ListColumnDescriptor<D, Object>();
                    final ComplexHeaderCellRenderer headerCellRenderer = new ComplexHeaderCellRenderer(form.getCaption(), new FormField(), form.getFieldPath());
                    columnDescriptor.setHeaderCellRenderer(headerCellRenderer);
                    columnDescriptor.setValueProvider(new BeanValueProvider<D, Object>(form.getFieldPath()) {
                        @Override
                        public Object getValue(D data) {
                            return super.getValue(data);
                        };
                    });
                    addCustomDescriptor(columnDescriptor);
                    return true;
                }
                return false;
            }

            @Override
            public void onCancel() {
            }
        });
        eventBus.fireEvent(new AddCustomColumnDescriptorEvent<D>(this));
    }

    @Override
    public void onRemoveColumnDescriptor(RemoveColumnDescriptorEvent event) {

    }
}