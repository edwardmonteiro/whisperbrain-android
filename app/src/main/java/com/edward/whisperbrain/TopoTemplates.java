package com.edward.whisperbrain;

import org.json.JSONArray;
import org.json.JSONObject;

/** Built-in analysis templates for a Plaud-style conversation notebook. */
public final class TopoTemplates {
    private TopoTemplates() {}

    public static final String[] NAMES = {
            "Executive meeting", "1:1", "Sales / client", "Decision review", "Brainstorm", "Interview"
    };

    public static String prompt(String name) {
        switch (name) {
            case "1:1": return "Extract goals, concerns, commitments, feedback, unresolved tensions, follow-ups and coaching opportunities.";
            case "Sales / client": return "Extract client needs, pains, objections, buying signals, stakeholders, commitments, risks, unanswered questions and next best actions.";
            case "Decision review": return "Extract the decision, options considered, evidence, assumptions, disagreements, risks, dependencies, owner and decision deadline.";
            case "Brainstorm": return "Cluster ideas, identify novel concepts, repeated themes, constraints, strongest hypotheses, experiments and next steps.";
            case "Interview": return "Extract claims, evidence, examples, strengths, gaps, follow-up questions and factual notes. Do not infer protected or sensitive traits.";
            default: return "Extract executive summary, decisions, actions with owners, risks, open questions, important facts, disagreements and next steps.";
        }
    }

    public static JSONObject schema() throws Exception {
        JSONObject str = new JSONObject().put("type", "string");
        JSONObject item = new JSONObject().put("type", "object")
                .put("properties", new JSONObject().put("text", str).put("owner", str).put("due", str))
                .put("required", new JSONArray().put("text").put("owner").put("due"))
                .put("additionalProperties", false);
        JSONObject section = new JSONObject().put("type", "array").put("items", str);
        JSONObject props = new JSONObject()
                .put("title", str)
                .put("summary", str)
                .put("decisions", section)
                .put("actions", new JSONObject().put("type", "array").put("items", item))
                .put("risks", section)
                .put("open_questions", section)
                .put("insights", section)
                .put("quotes_to_verify", section);
        return new JSONObject().put("type", "object").put("properties", props)
                .put("required", new JSONArray().put("title").put("summary").put("decisions").put("actions")
                        .put("risks").put("open_questions").put("insights").put("quotes_to_verify"))
                .put("additionalProperties", false);
    }
}
