/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup.list;

import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.popup.PopupIcons;
import com.intellij.ui.popup.WizardPopup;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;

public class ListPopupImpl extends WizardPopup implements ListPopup {

  private MyList myList;

  private MyMouseMotionListener myMouseMotionListener;
  private MyMouseListener myMouseListener;

  private ListPopupModel myListModel;

  private int myIndexForShowingChild = -1;
  private int myMaxRowCount = 20;
  private boolean myAutoHandleBeforeShow;


  public ListPopupImpl(ListPopupStep aStep, int maxRowCount) {
    super(aStep);
    if (maxRowCount != -1){
      myMaxRowCount = maxRowCount;
    }
  }

  public ListPopupImpl(ListPopupStep aStep) {
    super(aStep);
  }

  public ListPopupImpl(WizardPopup aParent, ListPopupStep aStep, Object parentValue) {
    super(aParent, aStep);
    setParentValue(parentValue);
  }

  public ListPopupImpl(WizardPopup aParent, ListPopupStep aStep, Object parentValue, int maxRowCount) {
    super(aParent, aStep);
    setParentValue(parentValue);
    if (maxRowCount != -1){
      myMaxRowCount = maxRowCount;
    }
  }

  ListPopupModel getListModel() {
    return myListModel;
  }

  protected boolean beforeShow() {
    myList.addMouseMotionListener(myMouseMotionListener);
    myList.addMouseListener(myMouseListener);

    myList.setVisibleRowCount(Math.min(myMaxRowCount, myListModel.getSize()));

    boolean shouldShow = super.beforeShow();
    if (myAutoHandleBeforeShow) {
      final boolean toDispose = tryToAutoSelect(true);
      shouldShow &= !toDispose;
    }

    return shouldShow;
  }

  protected void afterShow() {
    tryToAutoSelect(false);
  }

  private boolean tryToAutoSelect(boolean handleFinalChoices) {
    final int defaultIndex = getListStep().getDefaultOptionIndex();
    if (defaultIndex >= 0 && defaultIndex < myList.getModel().getSize()) {
      ListScrollingUtil.selectItem(myList, defaultIndex);
    }
    else {
      selectFirstSelectableItem();
    }

    if (getListStep().isAutoSelectionEnabled()) {
      if (!isVisible() && getSelectableCount() == 1) {
        return _handleSelect(handleFinalChoices);
      } else if (isVisible() && hasSingleSelectableItemWithSubmenu()) {
        return _handleSelect(handleFinalChoices);
      }
    }

    return false;
  }

  private boolean autoSelectUsingStatistics() {
    final String filter = getSpeedSearch().getFilter();
    if (!StringUtil.isEmpty(filter)) {
      int maxUseCount = -1;
      int mostUsedValue = -1;
      int elementsCount = myListModel.getSize();
      for (int i = 0; i < elementsCount; i++) {
        Object value = myListModel.getElementAt(i);
        final String text = getListStep().getTextFor(value);
        final int count =
            StatisticsManager.getInstance().getUseCount(new StatisticsInfo("#list_popup:" + myStep.getTitle() + "#" + filter, text));
        if (count > maxUseCount) {
          maxUseCount = count;
          mostUsedValue = i;
        }
      }

      if (mostUsedValue > 0) {
        ListScrollingUtil.selectItem(myList, mostUsedValue);
        return true;
      }
    }

    return false;
  }

  private void selectFirstSelectableItem() {
    for (int i = 0; i < myListModel.getSize(); i++) {
      if (getListStep().isSelectable(myListModel.getElementAt(i))) {
        myList.setSelectedIndex(i);
        break;
      }
    }
  }

  private boolean hasSingleSelectableItemWithSubmenu() {
    boolean oneSubmenuFound = false;
    int countSelectables = 0;
    for (int i = 0; i < myListModel.getSize(); i++) {
      Object elementAt = myListModel.getElementAt(i);
      if (getListStep().isSelectable(elementAt) ) {
        countSelectables ++;
        if (getStep().hasSubstep(elementAt)) {
          if (oneSubmenuFound) {
            return false;
          }
          oneSubmenuFound = true;
        }
      }
    }
    return oneSubmenuFound && countSelectables == 1;
  }

  private int getSelectableCount() {
    int count = 0;
    for (int i = 0; i < myListModel.getSize(); i++) {
      final Object each = myListModel.getElementAt(i);
      if (getListStep().isSelectable(each)) {
        count++;
      }
    }

    return count;
  }

  protected JComponent createContent() {
    myMouseMotionListener = new MyMouseMotionListener();
    myMouseListener = new MyMouseListener();

    myListModel = new ListPopupModel(this, getListStep());
    myList = new MyList();
    if (myStep.getTitle() != null) {
      myList.getAccessibleContext().setAccessibleName(myStep.getTitle());
    }
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myList.setSelectedIndex(0);
    myList.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    ListScrollingUtil.installActions(myList);

    myList.setCellRenderer(getListElementRenderer());

    myList.getActionMap().get("selectNextColumn").setEnabled(false);
    myList.getActionMap().get("selectPreviousColumn").setEnabled(false);

    registerAction("handleSelection1", KeyEvent.VK_ENTER, 0, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        handleSelect(true);
      }
    });

    registerAction("handleSelection2", KeyEvent.VK_RIGHT, 0, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        handleSelect(false);
      }
    });

    registerAction("goBack2", KeyEvent.VK_LEFT, 0, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (getParent() != null) {
          goBack();
        }
      }
    });


    myList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    return myList;
  }

  protected ActionMap getActionMap() {
    return myList.getActionMap();
  }

  protected InputMap getInputMap() {
    return myList.getInputMap();
  }

  protected ListCellRenderer getListElementRenderer() {
    return new PopupListElementRenderer(this);
  }

  public ListPopupStep<Object> getListStep() {
    return (ListPopupStep<Object>) myStep;
  }

  public void dispose() {
    myList.removeMouseMotionListener(myMouseMotionListener);
    myList.removeMouseListener(myMouseListener);
    super.dispose();
  }

  public void disposeChildren() {
    setIndexForShowingChild(-1);
    super.disposeChildren();
  }

  protected void onAutoSelectionTimer() {
    if (myList.getModel().getSize() > 0 && !myList.isSelectionEmpty() ) {
      handleSelect(false);
    }
    else {
      disposeChildren();
      setIndexForShowingChild(-1);
    }
  }

  public void handleSelect(boolean handleFinalChoices) {
    _handleSelect(handleFinalChoices);
  }

  private boolean _handleSelect(final boolean handleFinalChoices) {
    if (myList.getSelectedIndex() == -1) return false;

    if (myList.getSelectedIndex() == getIndexForShowingChild()) {
      return false;
    }

    final Object selectedValue = myList.getSelectedValue();
    if (!getListStep().isSelectable(selectedValue)) return false;

    if (!getListStep().hasSubstep(selectedValue) && !handleFinalChoices) return false;

    disposeChildren();

    if (myListModel.getSize() == 0) {
      disposeAllParents();
      setIndexForShowingChild(-1);
      return true;
    }

    valueSelected(selectedValue);

    return handleNextStep(myStep.onChosen(selectedValue, handleFinalChoices), selectedValue);
  }

  private void valueSelected(final Object value) {
    final String filter = getSpeedSearch().getFilter();
    if (!StringUtil.isEmpty(filter)) {
      final String text = getListStep().getTextFor(value);
      StatisticsManager.getInstance().incUseCount(new StatisticsInfo("#list_popup:" + getListStep().getTitle() + "#" + filter, text));
    }
  }

  private boolean handleNextStep(final PopupStep nextStep, Object parentValue) {
    if (nextStep != PopupStep.FINAL_CHOICE) {
      final Point point = myList.indexToLocation(myList.getSelectedIndex());
      SwingUtilities.convertPointToScreen(point, myList);
      myChild = createPopup(this, nextStep, parentValue);
      if (myChild instanceof ListPopupImpl) {
        for (ListSelectionListener listener : myList.getListSelectionListeners()) {
          ((ListPopupImpl)myChild).addListSelectionListener(listener);
        }
      }
      final JComponent container = getContent();
      assert container != null : "container == null";
      myChild.show(container, point.x + container.getWidth() - STEP_X_PADDING, point.y, true);
      setIndexForShowingChild(myList.getSelectedIndex());
      return false;
    }
    else {
      disposeAllParents();
      setIndexForShowingChild(-1);
      return true;
    }
  }

  public void addListSelectionListener(ListSelectionListener listSelectionListener) {
    myList.addListSelectionListener(listSelectionListener);
  }

  private class MyMouseMotionListener extends MouseMotionAdapter {
    public void mouseMoved(MouseEvent e) {
      Point point = e.getPoint();
      int index = myList.locationToIndex(point);

      if (index != myList.getSelectedIndex()) {
        myList.setSelectedIndex(index);
        restartTimer();
      }

      notifyParentOnChildSelection();
    }
  }

  private class MyMouseListener extends MouseAdapter {
    public void mouseClicked(MouseEvent e) {
      boolean handleFinalChoices = true;
      final Object selectedValue = myList.getSelectedValue();
      final ListPopupStep<Object> listStep = getListStep();
      if (listStep.hasSubstep(selectedValue) && listStep.isSelectable(selectedValue)) {
        final int index = myList.getSelectedIndex();
        final Rectangle bounds = myList.getCellBounds(index, index);
        final Point point = e.getPoint();
        if (point.getX() > bounds.width + bounds.getX() - PopupIcons.HAS_NEXT_ICON.getIconWidth()) { //press on handle icon
          handleFinalChoices = false;
        }
      }
      handleSelect(handleFinalChoices);
      stopTimer();
    }
  }

  protected void process(KeyEvent aEvent) {
    myList.processKeyEvent(aEvent);
  }

  private int getIndexForShowingChild() {
    return myIndexForShowingChild;
  }

  private void setIndexForShowingChild(int aIndexForShowingChild) {
    myIndexForShowingChild = aIndexForShowingChild;
  }

  private class MyList extends JList {
    public MyList() {
      super(myListModel);
    }

    public Dimension getPreferredScrollableViewportSize() {
      return new Dimension(super.getPreferredScrollableViewportSize().width, getPreferredSize().height);
    }

    public void processKeyEvent(KeyEvent e) {
      e.setSource(this);
      super.processKeyEvent(e);
    }
  }

  protected void onSpeedSearchPatternChanged() {
    myListModel.refilter();
    if (myListModel.getSize() > 0) {
      if (!autoSelectUsingStatistics()) {
        int fullMatchIndex = myListModel.getClosestMatchIndex();
        if (fullMatchIndex != -1) {
          myList.setSelectedIndex(fullMatchIndex);
        }

        if (myListModel.getSize() <= myList.getSelectedIndex() || !myListModel.isVisible(myList.getSelectedValue())) {
          myList.setSelectedIndex(0);
        }
      }
    }
  }

  protected void onSelectByMnemonic(Object value) {
    myList.setSelectedValue(value, true);
    myList.repaint();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        handleSelect(true);
      }
    });
  }

  protected JComponent getPreferredFocusableComponent() {
    return myList;
  }

  protected void onChildSelectedFor(Object value) {
    if (myList.getSelectedValue() != value) {
      myList.setSelectedValue(value, false);
    }
  }

  public void setHandleAutoSelectionBeforeShow(final boolean autoHandle) {
    myAutoHandleBeforeShow = autoHandle;
  }

  public boolean isModalContext() {
    return true;
  }

}
