package com.bladecoder.ink.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import com.bladecoder.ink.runtime.CallStack.Thread;

/**
 * All story state information is included in the StoryState class, including
 * global variables, read counts, the pointer to the current point in the story,
 * the call stack (for tunnels, functions, etc), and a few other smaller bits
 * and pieces. You can save the current state using the json serialisation
 * functions ToJson and LoadJson.
 */
public class StoryState {
	/**
	 * The current version of the state save file JSON-based format.
	 */
	public static final int kInkSaveStateVersion = 4;
	public static final int kMinCompatibleLoadVersion = 4;

	private Glue currentRightGlue;

	// REMEMBER! REMEMBER! REMEMBER!
	// When adding state, update the Copy method and serialisation
	// REMEMBER! REMEMBER! REMEMBER!
	private List<RTObject> outputStream;
	private CallStack callStack;
	private List<Choice> currentChoices;
	private List<String> currentErrors;
	private int currentTurnIndex;
	private boolean didSafeExit;
	private RTObject divertedTargetObject;
	private List<RTObject> evaluationStack;
	private Story story;
	private int storySeed;
	private HashMap<String, Integer> turnIndices;
	private VariablesState variablesState;
	private HashMap<String, Integer> visitCounts;

	StoryState(Story story) throws Exception {
		this.story = story;

		outputStream = new ArrayList<RTObject>();

		evaluationStack = new ArrayList<RTObject>();

		callStack = new CallStack(story.getRootContentContainer());
		variablesState = new VariablesState(callStack);

		visitCounts = new HashMap<String, Integer>();
		turnIndices = new HashMap<String, Integer>();
		currentTurnIndex = -1;

		// Seed the shuffle random numbers
		long timeSeed = System.currentTimeMillis();

		storySeed = new Random(timeSeed).nextInt() % 100;

		currentChoices = new ArrayList<Choice>();

		goToStart();
	}

	void addError(String message) {
		// TODO: Could just add to output?
		if (currentErrors == null) {
			currentErrors = new ArrayList<String>();
		}

		currentErrors.add(message);
	}

	// Warning: Any RTObject content referenced within the StoryState will
	// be re-referenced rather than cloned. This is generally okay though since
	// RTObjects are treated as immutable after they've been set up.
	// (e.g. we don't edit a Runtime.Text after it's been created an added.)
	// I wonder if there's a sensible way to enforce that..??
	StoryState copy() throws Exception {
		StoryState copy = new StoryState(story);

		copy.getOutputStream().addAll(outputStream);
		copy.currentChoices.addAll(currentChoices);

		if (hasError()) {
			copy.currentErrors = new ArrayList<String>();
			copy.currentErrors.addAll(currentErrors);
		}

		copy.callStack = new CallStack(callStack);

		copy.currentRightGlue = currentRightGlue;

		copy.variablesState = new VariablesState(copy.callStack);
		copy.variablesState.copyFrom(variablesState);

		copy.evaluationStack.addAll(evaluationStack);

		if (getDivertedTargetObject() != null)
			copy.setDivertedTargetObject(divertedTargetObject);

		copy.setPreviousContentObject(getPreviousContentObject());

		copy.visitCounts = new HashMap<String, Integer>(visitCounts);
		copy.turnIndices = new HashMap<String, Integer>(turnIndices);
		copy.currentTurnIndex = currentTurnIndex;
		copy.storySeed = storySeed;

		copy.setDidSafeExit(didSafeExit);

		return copy;
	}

	Container currentContainer() {
		return callStack.currentElement().currentContainer;
	}

	int currentGlueIndex() {
		for (int i = outputStream.size() - 1; i >= 0; i--) {
			RTObject c = outputStream.get(i);
			Glue glue = c instanceof Glue ? (Glue) c : null;
			if (glue != null)
				return i;
			else if (c instanceof ControlCommand) // e.g. BeginString
				break;
		}
		return -1;
	}

	String currentText() {
		StringBuilder sb = new StringBuilder();

		for (RTObject outputObj : outputStream) {
			StringValue textContent = outputObj instanceof StringValue ? (StringValue) outputObj : null;

			if (textContent != null) {
				sb.append(textContent.value);
			}
		}

		return sb.toString();
	}

	void forceEndFlow() throws Exception {
		setCurrentContentObject(null);

		while (callStack.canPopThread())
			callStack.PopThread();

		while (callStack.canPop())
			callStack.Pop();

		currentChoices.clear();

		setDidSafeExit(true);
	}

	RTObject getCurrentContentObject() {
		return callStack.currentElement().getCurrentRTObject();
	}

	Path getCurrentPath() {

		if (getCurrentContentObject() == null)
			return null;

		return getCurrentContentObject().getPath();
	}

	boolean getInExpressionEvaluation() {
		return callStack.currentElement().inExpressionEvaluation;
	}

	/**
	 * Object representation of full JSON state. Usually you should use LoadJson
	 * and ToJson since they serialise directly to String for you. But, if your
	 * game uses Json.Net itself, it may be useful to get the JToken so that you
	 * can integrate it into your own save format.
	 */
	public HashMap<String, Object> getJsonToken() throws Exception {

		HashMap<String, Object> obj = new HashMap<String, Object>();

		HashMap<String, Object> choiceThreads = null;
		for (Choice c : currentChoices) {
			c.originalChoicePath = c.getchoicePoint().getPath().getComponentsString();
			c.originalThreadIndex = c.getThreadAtGeneration().threadIndex;

			if (callStack.ThreadWithIndex(c.originalThreadIndex) == null) {
				if (choiceThreads == null)
					choiceThreads = new HashMap<String, Object>();

				choiceThreads.put(Integer.toString(c.originalThreadIndex), c.getThreadAtGeneration().jsonToken());
			}
		}
		if (choiceThreads != null)
			obj.put("choiceThreads", choiceThreads);

		obj.put("callstackThreads", callStack.GetJsonToken());
		obj.put("variablesState", variablesState.getjsonToken());

		obj.put("evalStack", Json.listToJArray(evaluationStack));

		obj.put("outputStream", Json.listToJArray(outputStream));

		obj.put("currentChoices", Json.listToJArray(currentChoices));

		if (currentRightGlue != null) {
			int rightGluePos = outputStream.indexOf(currentRightGlue);
			if (rightGluePos != -1) {
				obj.put("currRightGlue", outputStream.indexOf(currentRightGlue));
			}
		}

		if (getDivertedTargetObject() != null)
			obj.put("currentDivertTarget", getDivertedTargetObject().getPath().getComponentsString());

		obj.put("visitCounts", Json.intHashMapToJRTObject(visitCounts));
		obj.put("turnIndices", Json.intHashMapToJRTObject(turnIndices));
		obj.put("turnIdx", currentTurnIndex);
		obj.put("storySeed", storySeed);

		obj.put("inkSaveVersion", kInkSaveStateVersion);

		// Not using this right now, but could do in future.
		obj.put("inkFormatVersion", Story.inkVersionCurrent);

		return obj;
	}

	RTObject getPreviousContentObject() {
		return callStack.getcurrentThread().previousContentRTObject;
	}

	public HashMap<String, Integer> getVisitCounts() {
		return visitCounts;
	}

	void goToStart() {
		callStack.currentElement().currentContainer = story.mainContentContainer();
		callStack.currentElement().currentContentIndex = 0;
	}

	boolean hasError() {
		return currentErrors != null && currentErrors.size() > 0;
	}

	boolean inStringEvaluation() {
		for (int i = outputStream.size() - 1; i >= 0; i--) {
			ControlCommand cmd = outputStream.get(i) instanceof ControlCommand ? (ControlCommand) outputStream.get(i)
					: null;

			if (cmd != null && cmd.getcommandType() == ControlCommand.CommandType.BeginString) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Loads a previously saved state in JSON format.
	 * 
	 * @param json
	 *            The JSON String to load.
	 */
	public void loadJson(String json) throws Exception {
		setJsonToken(SimpleJson.textToHashMap(json));
	}

	List<Choice> getCurrentChoices() {
		return currentChoices;
	}
	
	List<String> getCurrentErrors() {
		return currentErrors;
	}

	List<RTObject> getOutputStream() {
		return outputStream;
	}
	
	CallStack getCallStack() {
		return callStack;
	}
	
	VariablesState getVariablesState() {
		return variablesState;
	}
	
	List<RTObject> getEvaluationStack() {
		return evaluationStack;
	}
	
	int getStorySeed() {
		return storySeed;
	}
	
	HashMap<String, Integer> getTurnIndices() {
		return turnIndices;
	}
	
	int getCurrentTurnIndex() {
		return currentTurnIndex;
	}

	boolean outputStreamContainsContent() {
		for (RTObject content : outputStream) {
			if (content instanceof StringValue)
				return true;
		}
		return false;
	}

	boolean outputStreamEndsInNewline() {
		if (outputStream.size() > 0) {

			for (int i = outputStream.size() - 1; i >= 0; i--) {
				RTObject obj = outputStream.get(i);
				if (obj instanceof ControlCommand) // e.g. BeginString
					break;
				StringValue text = outputStream.get(i) instanceof StringValue ? (StringValue) outputStream.get(i)
						: null;

				if (text != null) {
					if (text.getisNewline())
						return true;
					else if (text.getisNonWhitespace())
						break;
				}
			}
		}

		return false;
	}

	RTObject peekEvaluationStack() {
		return evaluationStack.get(evaluationStack.size() - 1);
	}

	RTObject popEvaluationStack() {
		RTObject obj = evaluationStack.get(evaluationStack.size() - 1);
		evaluationStack.remove(evaluationStack.size() - 1);
		return obj;
	}

	List<RTObject> popEvaluationStack(int numberOfObjects) throws Exception {
		if (numberOfObjects > evaluationStack.size()) {
			throw new Exception("trying to pop too many objects");
		}

		List<RTObject> popped = new ArrayList<RTObject>(
				evaluationStack.subList(evaluationStack.size() - numberOfObjects, evaluationStack.size()));
		evaluationStack.subList(evaluationStack.size() - numberOfObjects, evaluationStack.size()).clear();

		return popped;
	}

	void pushEvaluationStack(RTObject obj) {
		evaluationStack.add(obj);
	}

	// Push to output stream, but split out newlines in text for consistency
	// in dealing with them later.
	void pushToOutputStream(RTObject obj) {
		StringValue text = obj instanceof StringValue ? (StringValue) obj : null;

		if (text != null) {
			List<StringValue> listText = trySplittingHeadTailWhitespace(text);
			if (listText != null) {
				for (StringValue textObj : listText) {
					pushToOutputStreamIndividual(textObj);
				}
				return;
			}
		}

		pushToOutputStreamIndividual(obj);
	}

	void pushToOutputStreamIndividual(RTObject obj) {
		Glue glue = obj instanceof Glue ? (Glue) obj : null;
		StringValue text = obj instanceof StringValue ? (StringValue) obj : null;

		boolean includeInOutput = true;

		if (glue != null) {

			// Found matching left-glue for right-glue? Close it.
			boolean foundMatchingLeftGlue = glue.getisLeft() && currentRightGlue != null
					&& glue.getParent() == currentRightGlue.getParent();
			if (foundMatchingLeftGlue) {
				currentRightGlue = null;
			}

			// Left/Right glue is auto-generated for inline expressions
			// where we want to absorb newlines but only in a certain direction.
			// "Bi" glue is written by the user in their ink with <>
			if (glue.getisLeft() || glue.getisBi()) {
				trimNewlinesFromOutputStream(foundMatchingLeftGlue);
			}

			// New right-glue
			boolean isNewRightGlue = glue.getisRight() && currentRightGlue == null;
			if (isNewRightGlue) {
				currentRightGlue = glue;
			}

			includeInOutput = glue.getisBi() || isNewRightGlue;
		}

		else if (text != null) {

			if (currentGlueIndex() != -1) {

				// Absorb any new newlines if there's existing glue
				// in the output stream.
				// Also trim any extra whitespace (spaces/tabs) if so.
				if (text.getisNewline()) {
					trimFromExistingGlue();
					includeInOutput = false;
				}

				// Able to completely reset when
				else if (text.getisNonWhitespace()) {
					removeExistingGlue();
					currentRightGlue = null;
				}
			} else if (text.getisNewline()) {
				if (outputStreamEndsInNewline() || !outputStreamContainsContent())
					includeInOutput = false;
			}
		}

		if (includeInOutput) {
			outputStream.add(obj);
		}
	}

	// Only called when non-whitespace is appended
	void removeExistingGlue() {
		for (int i = outputStream.size() - 1; i >= 0; i--) {
			RTObject c = outputStream.get(i);
			if (c instanceof Glue) {
				outputStream.remove(i);
			} else if (c instanceof ControlCommand) { // e.g.
														// BeginString
				break;
			}
		}
	}

	void resetErrors() {
		currentErrors = null;
	}

	void resetOutput() {
		outputStream.clear();
	}

	// Don't make public since the method need to be wrapped in Story for visit
	// counting
	void setChosenPath(Path path) throws Exception {
		// Changing direction, assume we need to clear current set of choices
		currentChoices.clear();

		setCurrentPath(path);

		currentTurnIndex++;
	}

	void setCurrentContentObject(RTObject value) {
		callStack.currentElement().setcurrentRTObject(value);
	}

	void setCurrentPath(Path value) throws Exception {
		if (value != null)
			setCurrentContentObject(story.ContentAtPath(value));
		else
			setCurrentContentObject(null);
	}

	void setInExpressionEvaluation(boolean value) {
		callStack.currentElement().inExpressionEvaluation = value;
	}

	@SuppressWarnings("unchecked")
	void setJsonToken(HashMap<String, Object> value) throws StoryException, Exception {

		HashMap<String, Object> jObject = value;

		Object jSaveVersion = jObject.get("inkSaveVersion");

		if (jSaveVersion == null) {
			throw new StoryException("ink save format incorrect, can't load.");
		} else if ((int) jSaveVersion < kMinCompatibleLoadVersion) {
			throw new StoryException("Ink save format isn't compatible with the current version (saw '" + jSaveVersion
					+ "', but minimum is " + kMinCompatibleLoadVersion + "), so can't load.");
		}

		callStack.SetJsonToken((HashMap<String, Object>) jObject.get("callstackThreads"), story);
		variablesState.setjsonToken((HashMap<String, Object>) jObject.get("variablesState"));

		evaluationStack = Json.jArrayToRuntimeObjList((List<Object>) jObject.get("evalStack"));

		outputStream = Json.jArrayToRuntimeObjList((List<Object>) jObject.get("outputStream"));

		currentChoices = Json.jArrayToRuntimeObjList((List<Object>) jObject.get("currentChoices"));

		Object propValue = jObject.get("currRightGlue");
		if (propValue != null) {
			int gluePos = (int) propValue;
			if (gluePos >= 0) {
				currentRightGlue = (Glue) outputStream.get(gluePos);
			}
		}

		Object currentDivertTargetPath = jObject.get("currentDivertTarget");
		if (currentDivertTargetPath != null) {
			Path divertPath = new Path(currentDivertTargetPath.toString());
			setDivertedTargetObject(story.ContentAtPath(divertPath));
		}

		visitCounts = Json.jRTObjectToIntHashMap((HashMap<String, Object>) jObject.get("visitCounts"));
		turnIndices = Json.jRTObjectToIntHashMap((HashMap<String, Object>) jObject.get("turnIndices"));
		currentTurnIndex = (int) jObject.get("turnIdx");
		storySeed = (int) jObject.get("storySeed");

		Object jChoiceThreadsObj = jObject.get("choiceThreads");
		HashMap<String, Object> jChoiceThreads = (HashMap<String, Object>) jChoiceThreadsObj;

		for (Choice c : currentChoices) {
			c.setChoicePoint((ChoicePoint) story.ContentAtPath(new Path(c.originalChoicePath)));

			Thread foundActiveThread = callStack.ThreadWithIndex(c.originalThreadIndex);
			if (foundActiveThread != null) {
				c.setThreadAtGeneration(foundActiveThread);
			} else {
				HashMap<String, Object> jSavedChoiceThread = (HashMap<String, Object>) jChoiceThreads
						.get(Integer.toString(c.originalThreadIndex));
				c.setThreadAtGeneration(new CallStack.Thread(jSavedChoiceThread, story));
			}
		}

	}

	void setPreviousContentObject(RTObject value) {
		callStack.getcurrentThread().previousContentRTObject = value;
	}

	/**
	 * Exports the current state to json format, in order to save the game.
	 * 
	 * @returns The save state in json format.
	 */
	public String toJson() throws Exception {
		return SimpleJson.HashMapToText(getJsonToken());
	}

	void trimFromExistingGlue() {
		int i = currentGlueIndex();
		while (i < outputStream.size()) {
			StringValue txt = outputStream.get(i) instanceof StringValue ? (StringValue) outputStream.get(i) : null;

			if (txt != null && !txt.getisNonWhitespace())
				outputStream.remove(i);
			else
				i++;
		}
	}

	void trimNewlinesFromOutputStream(boolean stopAndRemoveRightGlue) {
		int removeWhitespaceFrom = -1;
		int rightGluePos = -1;
		boolean foundNonWhitespace = false;

		// Work back from the end, and try to find the point where
		// we need to start removing content. There are two ways:
		// - Start from the matching right-glue (because we just saw a
		// left-glue)
		// - Simply work backwards to find the first newline in a String of
		// whitespace
		int i = outputStream.size() - 1;
		while (i >= 0) {
			RTObject obj = outputStream.get(i);
			ControlCommand cmd = obj instanceof ControlCommand ? (ControlCommand) obj : null;
			StringValue txt = obj instanceof StringValue ? (StringValue) obj : null;
			Glue glue = obj instanceof Glue ? (Glue) obj : null;

			if (cmd != null || (txt != null && txt.getisNonWhitespace())) {
				foundNonWhitespace = true;

				if (!stopAndRemoveRightGlue)
					break;
			} else if (stopAndRemoveRightGlue && glue != null && glue.getisRight()) {
				rightGluePos = i;
				break;
			} else if (txt != null && txt.getisNewline() && !foundNonWhitespace) {
				removeWhitespaceFrom = i;
			}
			i--;
		}

		// Remove the whitespace
		if (removeWhitespaceFrom >= 0) {
			i = removeWhitespaceFrom;
			while (i < outputStream.size()) {
				StringValue text = outputStream.get(i) instanceof StringValue ? (StringValue) outputStream.get(i)
						: null;
				if (text != null) {
					outputStream.remove(i);
				} else {
					i++;
				}
			}
		}

		// Remove the glue (it will come before the whitespace,
		// so index is still valid)
		if (stopAndRemoveRightGlue && rightGluePos > -1)
			outputStream.remove(rightGluePos);
	}

	// At both the start and the end of the String, split out the new lines like
	// so:
	//
	// " \n \n \n the String \n is awesome \n \n "
	// ^-----------^ ^-------^
	//
	// Excess newlines are converted into single newlines, and spaces discarded.
	// Outside spaces are significant and retained. "Interior" newlines within
	// the main String are ignored, since this is for the purpose of gluing
	// only.
	//
	// - If no splitting is necessary, null is returned.
	// - A newline on its own is returned in an list for consistency.
	List<StringValue> trySplittingHeadTailWhitespace(StringValue single) {
		String str = single.value;

		int headFirstNewlineIdx = -1;
		int headLastNewlineIdx = -1;
		for (int i = 0; i < str.length(); ++i) {
			char c = str.charAt(i);
			if (c == '\n') {
				if (headFirstNewlineIdx == -1)
					headFirstNewlineIdx = i;
				headLastNewlineIdx = i;
			} else if (c == ' ' || c == '\t')
				continue;
			else
				break;
		}

		int tailLastNewlineIdx = -1;
		int tailFirstNewlineIdx = -1;
		for (int i = 0; i < str.length(); ++i) {
			char c = str.charAt(i);
			if (c == '\n') {
				if (tailLastNewlineIdx == -1)
					tailLastNewlineIdx = i;
				tailFirstNewlineIdx = i;
			} else if (c == ' ' || c == '\t')
				continue;
			else
				break;
		}

		// No splitting to be done?
		if (headFirstNewlineIdx == -1 && tailLastNewlineIdx == -1)
			return null;

		List<StringValue> listTexts = new ArrayList<StringValue>();
		int innerStrStart = 0;
		int innerStrEnd = str.length();

		if (headFirstNewlineIdx != -1) {
			if (headFirstNewlineIdx > 0) {
				StringValue leadingSpaces = new StringValue(str.substring(0, headFirstNewlineIdx));
				listTexts.add(leadingSpaces);
			}
			listTexts.add(new StringValue("\n"));
			innerStrStart = headLastNewlineIdx + 1;
		}

		if (tailLastNewlineIdx != -1) {
			innerStrEnd = tailFirstNewlineIdx;
		}

		if (innerStrEnd > innerStrStart) {
			String innerStrText = str.substring(innerStrStart, innerStrEnd);
			listTexts.add(new StringValue(innerStrText));
		}

		if (tailLastNewlineIdx != -1 && tailFirstNewlineIdx > headLastNewlineIdx) {
			listTexts.add(new StringValue("\n"));
			if (tailLastNewlineIdx < str.length() - 1) {
				int numSpaces = (str.length() - tailLastNewlineIdx) - 1;
				StringValue trailingSpaces = new StringValue(
						str.substring(tailLastNewlineIdx + 1, numSpaces + tailLastNewlineIdx + 1));
				listTexts.add(trailingSpaces);
			}
		}

		return listTexts;
	}

	/**
	 * Gets the visit/read count of a particular Container at the given path.
	 * For a knot or stitch, that path String will be in the form:
	 *
	 * knot knot.stitch
	 * 
	 * @return The number of times the specific knot or stitch has been
	 *         enountered by the ink engine.
	 * 
	 * @param pathString
	 *            The dot-separated path String of the specific knot or stitch.
	 *
	 */
	public int visitCountAtPathString(String pathString) {
		Integer visitCountOut = visitCounts.get(pathString);

		if (visitCountOut != null)
			return visitCountOut;

		return 0;
	}

	public RTObject getDivertedTargetObject() {
		return divertedTargetObject;
	}

	public void setDivertedTargetObject(RTObject divertedTargetObject) {
		this.divertedTargetObject = divertedTargetObject;
	}

	public boolean isDidSafeExit() {
		return didSafeExit;
	}

	public void setDidSafeExit(boolean didSafeExit) {
		this.didSafeExit = didSafeExit;
	}
}
