/*
 * Copyright (c) 2014, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.module.engagement.interaction.view.survey;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.apptentive.android.sdk.Log;
import com.apptentive.android.sdk.R;
import com.apptentive.android.sdk.model.Event;
import com.apptentive.android.sdk.model.SurveyResponse;
import com.apptentive.android.sdk.module.engagement.interaction.model.SurveyInteraction;
import com.apptentive.android.sdk.module.engagement.interaction.model.survey.*;
import com.apptentive.android.sdk.module.engagement.interaction.view.InteractionView;
import com.apptentive.android.sdk.module.metric.MetricModule;
import com.apptentive.android.sdk.module.survey.OnSurveyQuestionAnsweredListener;
import com.apptentive.android.sdk.storage.ApptentiveDatabase;
import com.apptentive.android.sdk.util.Util;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sky Kelsey
 */
public class SurveyInteractionView extends InteractionView<SurveyInteraction> {

	private static SurveyState surveyState;
	private static Map<String, String> data;

	//private OnSurveyFinishedListener onSurveyFinishedListener;

	public SurveyInteractionView(SurveyInteraction interaction) {
		super(interaction);
		if (surveyState == null) {
			surveyState = new SurveyState(interaction);
		}
		if (data == null) {
			data = new HashMap<String, String>();
			data.put("id", interaction.getId());
		}

	}

	@Override
	public void show(final Activity activity) {
		super.show(activity);

		if (interaction == null) {
			activity.finish();
			return;
		}

		activity.setContentView(R.layout.apptentive_survey);

		TextView title = (TextView) activity.findViewById(R.id.title);
		title.setFocusable(true);
		title.setFocusableInTouchMode(true);
		title.setText(interaction.getName());

		String descriptionText = interaction.getDescription();
		if (descriptionText != null) {
			TextView description = (TextView) activity.findViewById(R.id.description);
			description.setText(descriptionText);
			description.setVisibility(View.VISIBLE);
		}

		final Button send = (Button) activity.findViewById(R.id.send);
		send.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Util.hideSoftKeyboard(activity, view);

				if (interaction.isShowSuccessMessage() && interaction.getSuccessMessage() != null) {
					SurveyThankYouDialog dialog = new SurveyThankYouDialog(activity);
					dialog.setMessage(interaction.getSuccessMessage());
					dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
						@Override
						public void onDismiss(DialogInterface dialogInterface) {
							activity.finish();
						}
					});
					dialog.show();
				} else {
					activity.finish();
				}

				MetricModule.sendMetric(activity, Event.EventLabel.survey__submit, null, data);
				ApptentiveDatabase.getInstance(activity).addPayload(new SurveyResponse(interaction, surveyState));
				Log.d("Survey Submitted.");

/*
				TODO: How do we support this?
				if (SurveyModule.onSurveyFinishedListener != null) {
					SurveyModule.onSurveyFinishedListener.onSurveyFinished(true);
				}
*/
				cleanup();
			}
		});

		LinearLayout questions = (LinearLayout) activity.findViewById(R.id.questions);
		questions.removeAllViews();

		// Then render all the questions
		for (final Question question : interaction.getQuestions()) {
			if (question.getType() == Question.QUESTION_TYPE_SINGLELINE) {
				TextSurveyQuestionView textQuestionView = new TextSurveyQuestionView(activity, surveyState, (SinglelineQuestion) question);
				textQuestionView.setOnSurveyQuestionAnsweredListener(new OnSurveyQuestionAnsweredListener() {
					public void onAnswered() {
						sendMetricForQuestion(activity, question);
						send.setEnabled(isSurveyValid());
					}
				});
				questions.addView(textQuestionView);
			} else if (question.getType() == Question.QUESTION_TYPE_MULTICHOICE) {
				MultichoiceSurveyQuestionView multichoiceQuestionView = new MultichoiceSurveyQuestionView(activity, surveyState, (MultichoiceQuestion) question);
				multichoiceQuestionView.setOnSurveyQuestionAnsweredListener(new OnSurveyQuestionAnsweredListener() {
					public void onAnswered() {
						sendMetricForQuestion(activity, question);
						send.setEnabled(isSurveyValid());
					}
				});
				questions.addView(multichoiceQuestionView);
			} else if (question.getType() == Question.QUESTION_TYPE_MULTISELECT) {
				MultiselectSurveyQuestionView multiselectQuestionView = new MultiselectSurveyQuestionView(activity, surveyState, (MultiselectQuestion) question);
				multiselectQuestionView.setOnSurveyQuestionAnsweredListener(new OnSurveyQuestionAnsweredListener() {
					public void onAnswered() {
						sendMetricForQuestion(activity, question);
						send.setEnabled(isSurveyValid());
					}
				});
				questions.addView(multiselectQuestionView);
			}
		}
		MetricModule.sendMetric(activity, Event.EventLabel.survey__launch, null, data);

		send.setEnabled(isSurveyValid());

		// Force the top of the survey to be shown first.
		title.requestFocus();
	}

	public SurveyState getSurveyState() {
		return this.surveyState;
	}

	public boolean isSurveyValid() {
		for (Question question : interaction.getQuestions()) {
			if (!surveyState.isQuestionValid(question)) {
				return false;
			}
		}
		return true;
	}

	void sendMetricForQuestion(Context context, Question question) {
		String questionId = question.getId();
		if (!surveyState.isMetricSent(questionId) && surveyState.isQuestionValid(question)) {
			Map<String, String> answerData = new HashMap<String, String>();
			answerData.put("id", question.getId());
			answerData.put("survey_id", interaction.getId());
			MetricModule.sendMetric(context, Event.EventLabel.survey__question_response, null, answerData);
			surveyState.markMetricSent(questionId);
		}
	}

	private void cleanup() {
		surveyState = null;
		//this.onSurveyFinishedListener = null;
		data = null;
	}


	@Override
	public void onStop() {

	}

	@Override
	public void onBackPressed(Activity activity) {
		MetricModule.sendMetric(activity, Event.EventLabel.survey__cancel, null, data);
/*
		TODO: How do we support this?
		if (SurveyModule.this.onSurveyFinishedListener != null) {
			SurveyModule.this.onSurveyFinishedListener.onSurveyFinished(false);
		}
*/
		cleanup();
	}
}