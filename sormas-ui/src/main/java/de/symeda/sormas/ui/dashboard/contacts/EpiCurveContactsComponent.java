package de.symeda.sormas.ui.dashboard.contacts;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.vaadin.hene.popupbutton.PopupButton;

import com.vaadin.ui.OptionGroup;
import com.vaadin.ui.VerticalLayout;

import de.symeda.sormas.api.FacadeProvider;
import de.symeda.sormas.api.contact.ContactClassification;
import de.symeda.sormas.api.contact.ContactCriteria;
import de.symeda.sormas.api.contact.ContactStatus;
import de.symeda.sormas.api.contact.FollowUpStatus;
import de.symeda.sormas.api.utils.DateHelper;
import de.symeda.sormas.ui.dashboard.DashboardDataProvider;
import de.symeda.sormas.ui.dashboard.diagram.AbstractEpiCurveComponent;
import de.symeda.sormas.ui.dashboard.diagram.EpiCurveGrouping;
import de.symeda.sormas.ui.login.LoginHelper;
import de.symeda.sormas.ui.utils.CssStyles;

public class EpiCurveContactsComponent extends AbstractEpiCurveComponent {

	private static final long serialVersionUID = 6582975657305031105L;

	private EpiCurveContactsMode epiCurveContactsMode;

	public EpiCurveContactsComponent(DashboardDataProvider dashboardDataProvider) {
		super(dashboardDataProvider);
	}

	@Override
	protected PopupButton createEpiCurveModeSelector() {
		if (epiCurveContactsMode == null) {
			epiCurveContactsMode = EpiCurveContactsMode.FOLLOW_UP_STATUS;
			epiCurveLabel.setValue(epiCurveContactsMode.toString() + " Curve");
		}

		PopupButton dataDropdown = new PopupButton("Data");
		CssStyles.style(dataDropdown, CssStyles.BUTTON_SUBTLE);

		VerticalLayout groupingLayout = new VerticalLayout();
		groupingLayout.setMargin(true);
		groupingLayout.setSizeUndefined();
		dataDropdown.setContent(groupingLayout);

		OptionGroup dataSelect = new OptionGroup();
		dataSelect.setWidth(100, Unit.PERCENTAGE);
		dataSelect.addItems((Object[]) EpiCurveContactsMode.values());
		dataSelect.setValue(epiCurveContactsMode);
		dataSelect.select(epiCurveContactsMode);
		dataSelect.addValueChangeListener(e -> {
			epiCurveContactsMode = (EpiCurveContactsMode) e.getProperty().getValue();
			epiCurveLabel.setValue(epiCurveContactsMode.toString() + " Curve");
			clearAndFillEpiCurveChart();
		});
		groupingLayout.addComponent(dataSelect);

		return dataDropdown;
	}

	@Override
	public void clearAndFillEpiCurveChart() {
		StringBuilder hcjs = new StringBuilder();
		hcjs.append(
				"var options = {"
						+ "chart:{ "
						+ " type: 'column', "
						+ " backgroundColor: 'transparent' "
						+ "},"
						+ "credits:{ enabled: false },"
						+ "exporting:{ "
						+ " enabled: true,"
						+ " buttons:{ contextButton:{ theme:{ fill: 'transparent' } } }"
						+ "},"
						+ "title:{ text: '' },"
				);

		// Creates and sets the labels for each day on the x-axis
		List<Date> filteredDates = buildListOfFilteredDates();
		List<String> newLabels = new ArrayList<>();
		Calendar calendar = Calendar.getInstance();
		for (Date date : filteredDates) {
			if (epiCurveGrouping == EpiCurveGrouping.DAY) {
				String label = DateHelper.formatLocalShortDate(date);
				newLabels.add(label);
			} else if (epiCurveGrouping == EpiCurveGrouping.WEEK) {
				calendar.setTime(date);
				String label = DateHelper.getEpiWeek(date).toShortString();
				newLabels.add(label);
			} else {
				String label = DateHelper.formatDateWithMonthAbbreviation(date);
				newLabels.add(label);
			}
		}

		hcjs.append("xAxis: { categories: [");
		for (String s : newLabels) {
			if (newLabels.indexOf(s) == newLabels.size() - 1) {
				hcjs.append("'" + s + "']},");
			} else {
				hcjs.append("'" + s + "', ");
			}
		}

		hcjs.append("yAxis: { min: 0, title: { text: 'Number of Contacts' }, allowDecimals: false, softMax: 10, "
				+ "stackLabels: { enabled: true, "
				+ "style: {fontWeight: 'normal', textOutline: '0', gridLineColor: '#000000', color: (Highcharts.theme && Highcharts.theme.textColor) || 'gray' } } },"
				+ "legend: { verticalAlign: 'top', backgroundColor: 'transparent', align: 'left', "
				+ "borderWidth: 0, shadow: false, margin: 30, padding: 0 },"
				+ "tooltip: { headerFormat: '<b>{point.x}</b><br/>', pointFormat: '{series.name}: {point.y}<br/>Total: {point.stackTotal}'},"
				+ "plotOptions: { column: { borderWidth: 0, stacking: 'normal', groupPadding: 0, pointPadding: 0, dataLabels: {"
				+ "enabled: true, formatter: function() { if (this.y > 0) return this.y; },"
				+ "color: (Highcharts.theme && Highcharts.theme.dataLabelsColor) || 'white' } } },");

		if (epiCurveContactsMode == EpiCurveContactsMode.CONTACT_CLASSIFICATION) {
			int[] unconfirmedNumbers = new int[newLabels.size()];
			int[] confirmedNumbers = new int[newLabels.size()];

			for (int i = 0; i < filteredDates.size(); i++) {
				Date date = filteredDates.get(i);

				ContactCriteria contactCriteria = new ContactCriteria()
						.caseDiseaseEquals(dashboardDataProvider.getDisease())
						.caseRegion(dashboardDataProvider.getRegion())
						.caseDistrict(dashboardDataProvider.getDistrict());
				if (epiCurveGrouping == EpiCurveGrouping.DAY) {
					contactCriteria.reportDateBetween(DateHelper.getStartOfDay(date), DateHelper.getEndOfDay(date));
				} else if (epiCurveGrouping == EpiCurveGrouping.WEEK) {
					contactCriteria.reportDateBetween(DateHelper.getStartOfWeek(date), DateHelper.getEndOfWeek(date));
				} else {
					contactCriteria.reportDateBetween(DateHelper.getStartOfMonth(date), DateHelper.getEndOfMonth(date));
				}

				Map<ContactClassification, Long> contactCounts = FacadeProvider.getContactFacade()
						.getNewContactCountPerClassification(contactCriteria, LoginHelper.getCurrentUser().getUuid());

				Long unconfirmedCount = contactCounts.get(ContactClassification.UNCONFIRMED);
				Long confirmedCount = contactCounts.get(ContactClassification.CONFIRMED);
				unconfirmedNumbers[i] = unconfirmedCount != null ? unconfirmedCount.intValue() : 0;
				confirmedNumbers[i] = confirmedCount != null ? confirmedCount.intValue() : 0;
			}

			hcjs.append("series: [");
			hcjs.append("{ name: 'Unconfirmed', color: '#FFD700', dataLabels: { allowOverlap: false }, data: [");
			for (int i = 0; i < unconfirmedNumbers.length; i++) {
				if (i == unconfirmedNumbers.length - 1) {
					hcjs.append(unconfirmedNumbers[i] + "]},");
				} else {
					hcjs.append(unconfirmedNumbers[i] + ", ");
				}
			}
			hcjs.append("{ name: 'Confirmed', color: '#B22222', dataLabels: { allowOverlap: false },  data: [");
			for (int i = 0; i < confirmedNumbers.length; i++) {
				if (i == confirmedNumbers.length - 1) {
					hcjs.append(confirmedNumbers[i] + "]}]};");
				} else {
					hcjs.append(confirmedNumbers[i] + ", ");
				}
			}
		} else if (epiCurveContactsMode == EpiCurveContactsMode.FOLLOW_UP_STATUS) {
			int[] underFollowUpNumbers = new int[newLabels.size()];
			int[] lostToFollowUpNumbers = new int[newLabels.size()];
			int[] completedFollowUpNumbers = new int[newLabels.size()];
			int[] canceledFollowUpNumbers = new int[newLabels.size()];
			int[] convertedNumbers = new int[newLabels.size()];

			for (int i = 0; i < filteredDates.size(); i++) {
				Date date = filteredDates.get(i);

				ContactCriteria contactCriteria = new ContactCriteria()
						.caseDiseaseEquals(dashboardDataProvider.getDisease())
						.caseRegion(dashboardDataProvider.getRegion())
						.caseDistrict(dashboardDataProvider.getDistrict());
				if (epiCurveGrouping == EpiCurveGrouping.DAY) {
					contactCriteria.reportDateBetween(DateHelper.getStartOfDay(date), DateHelper.getEndOfDay(date));
				} else if (epiCurveGrouping == EpiCurveGrouping.WEEK) {
					contactCriteria.reportDateBetween(DateHelper.getStartOfWeek(date), DateHelper.getEndOfWeek(date));
				} else {
					contactCriteria.reportDateBetween(DateHelper.getStartOfMonth(date), DateHelper.getEndOfMonth(date));
				}

				Map<FollowUpStatus, Long> contactCounts = FacadeProvider.getContactFacade()
						.getNewContactCountPerFollowUpStatus(contactCriteria, LoginHelper.getCurrentUser().getUuid());
				Map<ContactStatus, Long> contactStatusCounts = FacadeProvider.getContactFacade()
						.getNewContactCountPerStatus(contactCriteria, LoginHelper.getCurrentUser().getUuid());

				Long underFollowUpCount = contactCounts.get(FollowUpStatus.FOLLOW_UP);
				Long lostToFollowUpCount = contactCounts.get(FollowUpStatus.LOST);
				Long completedFollowUpCount = contactCounts.get(FollowUpStatus.COMPLETED);
				Long canceledFollowUpCount = contactCounts.get(FollowUpStatus.CANCELED);
				Long convertedCount = contactStatusCounts.get(ContactStatus.CONVERTED);
				underFollowUpNumbers[i] = underFollowUpCount != null ? underFollowUpCount.intValue() : 0;
				lostToFollowUpNumbers[i] = lostToFollowUpCount != null ? lostToFollowUpCount.intValue() : 0;
				completedFollowUpNumbers[i] = completedFollowUpCount != null ? completedFollowUpCount.intValue() : 0;
				canceledFollowUpNumbers[i] = canceledFollowUpCount != null ? canceledFollowUpCount.intValue() : 0;
				convertedNumbers[i] = convertedCount != null ? convertedCount.intValue() : 0;
			}

			hcjs.append("series: [");
			hcjs.append("{ name: 'Under F/U', color: '#005A9C', dataLabels: { allowOverlap: false }, data: [");
			for (int i = 0; i < underFollowUpNumbers.length; i++) {
				if (i == underFollowUpNumbers.length - 1) {
					hcjs.append(underFollowUpNumbers[i] + "]},");
				} else {
					hcjs.append(underFollowUpNumbers[i] + ", ");
				}
			}
			hcjs.append("{ name: 'Lost To F/U', color: '#FF0000', dataLabels: { allowOverlap: false },  data: [");
			for (int i = 0; i < lostToFollowUpNumbers.length; i++) {
				if (i == lostToFollowUpNumbers.length - 1) {
					hcjs.append(lostToFollowUpNumbers[i] + "]},");
				} else {
					hcjs.append(lostToFollowUpNumbers[i] + ", ");
				}
			}
			hcjs.append("{ name: 'Completed F/U', color: '#32CD32', dataLabels: { allowOverlap: false },  data: [");
			for (int i = 0; i < completedFollowUpNumbers.length; i++) {
				if (i == completedFollowUpNumbers.length - 1) {
					hcjs.append(completedFollowUpNumbers[i] + "]},");
				} else {
					hcjs.append(completedFollowUpNumbers[i] + ", ");
				}
			}
			hcjs.append("{ name: 'Canceled F/U', color: '#FF8C00', dataLabels: { allowOverlap: false },  data: [");
			for (int i = 0; i < canceledFollowUpNumbers.length; i++) {
				if (i == canceledFollowUpNumbers.length - 1) {
					hcjs.append(canceledFollowUpNumbers[i] + "]},");
				} else {
					hcjs.append(canceledFollowUpNumbers[i] + ", ");
				}
			}
			hcjs.append("{ name: 'Converted to Case', color: '#00BFFF', dataLabels: { allowOverlap: false },  data: [");
			for (int i = 0; i < convertedNumbers.length; i++) {
				if (i == convertedNumbers.length - 1) {
					hcjs.append(convertedNumbers[i] + "]}]};");
				} else {
					hcjs.append(convertedNumbers[i] + ", ");
				}
			}
		} else if (epiCurveContactsMode == EpiCurveContactsMode.FOLLOW_UP_UNTIL) {
			int[] followUpUntilNumbers = new int[newLabels.size()];
// TODO month grouping too few contacts, epi week grouping not showing lost ones
			ContactCriteria contactCriteria = new ContactCriteria()
					.caseDiseaseEquals(dashboardDataProvider.getDisease())
					.caseRegion(dashboardDataProvider.getRegion())
					.caseDistrict(dashboardDataProvider.getDistrict());
			if (epiCurveGrouping == EpiCurveGrouping.DAY) {
				contactCriteria.followUpUntilBetween(DateHelper.getStartOfDay(filteredDates.get(0)), DateHelper.getEndOfDay(filteredDates.get(filteredDates.size() - 1)));
			} else if (epiCurveGrouping == EpiCurveGrouping.WEEK) {
				contactCriteria.followUpUntilBetween(DateHelper.getStartOfWeek(filteredDates.get(0)), DateHelper.getEndOfWeek(filteredDates.get(filteredDates.size() - 1)));
			} else {
				contactCriteria.followUpUntilBetween(DateHelper.getStartOfMonth(filteredDates.get(0)), DateHelper.getEndOfMonth(filteredDates.get(filteredDates.size() - 1)));
			}

			Map<Date, Long> followUpUntilDateCounts = FacadeProvider.getContactFacade()
					.getFollowUpUntilCountPerDate(contactCriteria, LoginHelper.getCurrentUser().getUuid());

			for (Date date : followUpUntilDateCounts.keySet()) {
				Long followUpUntilCount = followUpUntilDateCounts.get(date);
				int filteredDatesIndex = getFilteredDatesIndexForDate(filteredDates, 0, filteredDates.size() - 1, date);
				followUpUntilNumbers[filteredDatesIndex] = followUpUntilCount != null ? followUpUntilCount.intValue() : 0;
			}

			hcjs.append("series: [");
			hcjs.append("{ name: 'F/U Until', color: '#00BFFF', dataLabels: { allowOverlap: false },  data: [");
			for (int i = 0; i < followUpUntilNumbers.length; i++) {
				if (i == followUpUntilNumbers.length - 1) {
					hcjs.append(followUpUntilNumbers[i] + "]}]};");
				} else {
					hcjs.append(followUpUntilNumbers[i] + ", ");
				}
			}
		}

		epiCurveChart.setHcjs(hcjs.toString());	
	}

	private int getFilteredDatesIndexForDate(List<Date> filteredDates, int startIndex, int endIndex, Date date) {
		int middleIndex = (startIndex + endIndex) / 2;

		if (epiCurveGrouping == EpiCurveGrouping.DAY) {
			if (filteredDates.get(middleIndex).equals(date)) {
				return middleIndex;
			} else if (filteredDates.get(middleIndex).before(date)) {
				return getFilteredDatesIndexForDate(filteredDates, middleIndex, endIndex, date);
			} else {
				return getFilteredDatesIndexForDate(filteredDates, startIndex, middleIndex, date);
			}
		} else if (epiCurveGrouping == EpiCurveGrouping.WEEK) {
			if (DateHelper.isBetween(date, 
					DateHelper.getStartOfWeek(filteredDates.get(middleIndex)), 
					DateHelper.getEndOfWeek(filteredDates.get(middleIndex)))) {
				return middleIndex;
			} else if (DateHelper.getStartOfWeek(filteredDates.get(middleIndex)).before(date)) {
				return getFilteredDatesIndexForDate(filteredDates, middleIndex, endIndex, date);
			} else {
				return getFilteredDatesIndexForDate(filteredDates, startIndex, middleIndex, date);
			}
		} else {
			if (DateHelper.isBetween(date, 
					DateHelper.getStartOfMonth(filteredDates.get(middleIndex)), 
					DateHelper.getEndOfMonth(filteredDates.get(middleIndex)))) {
				return middleIndex;
			} else if (DateHelper.getStartOfMonth(filteredDates.get(middleIndex)).before(date)) {
				return getFilteredDatesIndexForDate(filteredDates, middleIndex, endIndex, date);
			} else {
				return getFilteredDatesIndexForDate(filteredDates, startIndex, middleIndex, date);
			}
		}
	}


}