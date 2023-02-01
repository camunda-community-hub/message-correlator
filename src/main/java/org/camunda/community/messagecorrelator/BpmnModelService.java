package org.camunda.community.messagecorrelator;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.*;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import space.earlygrey.simplegraphs.Graph;
import space.earlygrey.simplegraphs.Path;

public class BpmnModelService {

  private static final Logger LOG = LoggerFactory.getLogger(BpmnModelService.class);

  private final BpmnModelInstance modelInstance;

  private MyDirectedGraph<String> processAsGraph;
  Map<String, Pair<List<String>, List<String>>> replacements = new HashMap<>();
  Map<String, Set<String>> predecessorToBeRemoved = new HashMap<>();

  public BpmnModelService(String pathBpmnFile) {
    modelInstance = readModel(pathBpmnFile);
    createGraph();
  }

  /**
   * if 1 candidate gets returned -> this is the targetBpmnElementId if N candidates get returned ->
   * have to find the shortest path next step
   */
  public List<String> determineBpmnElementIdsForMessage(String message) {
    Collection<CatchEvent> catchEvents = modelInstance.getModelElementsByType(CatchEvent.class);
    List<String> result = new ArrayList<>();
    catchEvents.stream()
        .filter(
            ce ->
                ce instanceof IntermediateCatchEvent
                    || (ce instanceof BoundaryEvent && ((BoundaryEvent) ce).cancelActivity())
                    || (ce instanceof StartEvent
                        && ce.getEventDefinitions().size() == 1)) // BPMN v2.0.2 pp. 394-398
        .forEach(
            ce -> {
              Collection<EventDefinition> eventDefinitions = ce.getEventDefinitions();

              EventDefinition eventDefinition = eventDefinitions.stream().findFirst().get();
              if (eventDefinition instanceof MessageEventDefinition
                  && ((MessageEventDefinition) eventDefinition)
                      .getMessage()
                      .getName()
                      .equals(message)) {
                LOG.info("{} is a message with bpmnElementId={}", ce.getName(), ce.getId());
                result.add(ce.getId());
              }
            });

    // FIXME: This is the only case in which same Message (PREADVICE) can lead to 2 followsups (in
    //  this case: within event-based subprocess)
    result.removeIf(bpmnElementId -> bpmnElementId.equals("Event_06wf1r6"));

    return result;
  }

  public List<String> determineMessagesToBeSent(
      String currentlyActiveBpmnElementId, String targetMessage) {
    List<String> targetBpmnIdCandidates = determineBpmnElementIdsForMessage(targetMessage);

    return findShortestPathForElementIds(currentlyActiveBpmnElementId, targetBpmnIdCandidates)
        .stream()
        .map(this::getAssociatedMessageForBpmnElementId)
        .flatMap(Optional::stream)
        .collect(Collectors.toList());
  }

  private BpmnModelInstance readModel(String pathBpmnFile) {
    File file = new File(pathBpmnFile);
    return Bpmn.readModelFromFile(file);
  }

  private void createGraph() {

    processAsGraph = new MyDirectedGraph<>();

    // Resolve parent process (starting point = StartEvent of process)
    Set<FlowNode> currentNodes =
        modelInstance.getModelElementsByType(StartEvent.class).stream()
            .filter(el -> !(el.getParentElement() instanceof SubProcess))
            .collect(Collectors.toSet());
    transformIntoGraph(processAsGraph, currentNodes);

    // Resolve sub processes
    Collection<SubProcess> subProcesses = modelInstance.getModelElementsByType(SubProcess.class);
    subProcesses.forEach(
        subProcess -> {
          Set<FlowNode> startEvents =
              new HashSet<>(subProcess.getChildElementsByType(StartEvent.class));
          transformIntoGraph(processAsGraph, startEvents);
        });

    // Resolve boundary events
    Collection<BoundaryEvent> boundaryEvents =
        modelInstance.getModelElementsByType(BoundaryEvent.class);
    // Take each boundary event as a starting point
    transformIntoGraph(processAsGraph, new HashSet<>(boundaryEvents));
    boundaryEvents.forEach(
        boundaryEvent -> {
          // Find the element to which the boundary event is attached
          Activity attachedTo = boundaryEvent.getAttachedTo();
          Collection<FlowNode> possiblePredecessors = Set.of();
          if (attachedTo instanceof Task) {
            // If the event is attached to a task, the task's predecessors become the events
            // predecessors
            possiblePredecessors =
                attachedTo.getIncoming().stream()
                    .map(SequenceFlow::getSource)
                    .collect(Collectors.toSet());
          } else if (attachedTo instanceof SubProcess) {
            // If the event is attached to a subprocess, all child elements of the subprocess are
            // possible predecessors
            possiblePredecessors = attachedTo.getChildElementsByType(FlowNode.class);
            possiblePredecessors.removeIf(
                fn -> fn instanceof SubProcess && ((SubProcess) fn).triggeredByEvent());

            possiblePredecessors.forEach(
                previousNode -> {
                  // If the predecessor is a catch event, we must ignore it on the paths that go via
                  // the boundary event
                  if (previousNode instanceof IntermediateCatchEvent) {
                    // PredecessorsToBeRemoved is a data structure used during post-processing.
                    predecessorToBeRemoved
                        .computeIfAbsent(boundaryEvent.getId(), eventId -> new HashSet<>())
                        .add(previousNode.getId());
                  }
                });
          }
          // Connect the vertex of the boundary event to its predecessors
          possiblePredecessors.forEach(
              previousNode -> {
                LOG.debug("Adding edge: {} --> {}", previousNode.getId(), boundaryEvent.getId());
                processAsGraph.addEdge(previousNode.getId(), boundaryEvent.getId());
              });
        });

    LOG.debug("");
    LOG.debug("=========== STATUS ===========");
    LOG.debug("Graph should be fully resolved now");
    LOG.debug("Next: Replace Subprocess");
    LOG.debug("");
    LOG.debug("");

    // Replace Subprocesses
    // Find subprocess
    subProcesses.stream()
        .filter(subProcess -> !subProcess.triggeredByEvent())
        .forEach(
            subProcess -> {
              // Find startElement (Replacement1) of Subprocess
              StartEvent startEvent =
                  subProcess.getChildElementsByType(StartEvent.class).stream()
                      .findFirst()
                      .get(); // SubProcess has to have 1 StartEvent
              // Find edge entering subprocess
              Collection<SequenceFlow> incomingToSubProcess = subProcess.getIncoming();

              // Find endEvent (Replacement2) of Subprocess
              Collection<EndEvent> endEvents = subProcess.getChildElementsByType(EndEvent.class);
              // Find edge leaving subprocess
              SequenceFlow outgoingFromSubProcess =
                  subProcess.getOutgoing().stream().findFirst().get(); // TODO could be more

              // Connect incoming edges with startEvent
              incomingToSubProcess.forEach(
                  incomingSequenceFlow ->
                      addEdge(incomingSequenceFlow.getSource().getId(), startEvent.getId()));
              // Connect endEvent with outgoing edges
              endEvents.forEach(
                  endEvent -> {
                    addEdge(endEvent.getId(), outgoingFromSubProcess.getTarget().getId());
                    removeVertexAndAddReplacements(
                        subProcess.getId(), Pair.of(startEvent.getId(), endEvent.getId()));
                  });
            });

    LOG.debug("");
    LOG.debug("=========== STATUS ===========");
    LOG.debug("Subprocess should be replaced now");
    LOG.debug("Next: Replace StartEvents");
    LOG.debug("");
    LOG.debug("");

    // Replace StartEvents
    List<StartEvent> startEventsToBeRemoved = getStartEventsToBeRemoved();
    removeFlowNodesFromGraph(startEventsToBeRemoved);

    LOG.debug("");
    LOG.debug("=========== STATUS ===========");
    LOG.debug("StartEvents should be replaced now");
    LOG.debug("Next: Replace EndEvents");
    LOG.debug("");
    LOG.debug("");

    // Replace EndEvents
    removeFlowNodesOfTypeFromGraph(EndEvent.class);

    LOG.debug("");
    LOG.debug("=========== STATUS ===========");
    LOG.debug("EndEvents should be replaced now");
    LOG.debug("Next: Replace Gateways");
    LOG.debug("");
    LOG.debug("");

    // Replace Gateways
    removeFlowNodesOfTypeFromGraph(Gateway.class);

    LOG.debug("");
    LOG.debug("=========== STATUS ===========");
    LOG.debug("Gateways should be replaced now");
    LOG.debug("Next: Replace Tasks");
    LOG.debug("");
    LOG.debug("");

    // Replace
    removeFlowNodesOfTypeFromGraph(Task.class);

    LOG.debug("");
    LOG.debug("=========== STATUS ===========");
    LOG.debug("Tasks should be replaced now");
    LOG.debug("");
    LOG.debug("");
    LOG.info("Process should now be fully transformed!");
  }

  /**
   * @return
   */
  private List<StartEvent> getStartEventsToBeRemoved() {
    return modelInstance.getModelElementsByType(StartEvent.class).stream()
        .filter(
            startEvent -> {
              List<EventDefinition> eventDefinitions =
                  new ArrayList<>(startEvent.getEventDefinitions());
              // if start event is a message start event, it should not be removed
              if (eventDefinitions.size() > 0) {
                EventDefinition eventDefinition = eventDefinitions.get(0);
                return !(eventDefinition instanceof MessageEventDefinition);
              } else {
                return true;
              }
            })
        .collect(Collectors.toList());
  }

  private void removeVertexAndAddReplacements(
      String vertexToBeReplaced, Pair<String, String> replacement) {
    //        LOG.debug("Trying to Remove vertex: {}", vertexToBeReplaced);
    boolean vertexInGraph = processAsGraph.contains(vertexToBeReplaced);

    // if vertex is in graph, we haven't been here before
    if (vertexInGraph) {
      processAsGraph.removeVertex(vertexToBeReplaced);
      LOG.debug(
          "Removing vertex and adding Replacement: vertex={} >> replacement={}",
          vertexToBeReplaced,
          replacement);
    } else {
      LOG.debug("Adding Replacement: vertex={} >> replacement={}", vertexToBeReplaced, replacement);
    }
    addReplacement(vertexToBeReplaced, replacement);
  }

  private <T extends FlowNode> void removeFlowNodesFromGraph(Collection<T> flowNodes) {
    flowNodes.stream().map(FlowNode::getId).forEach(processAsGraph::removeVertexFromPath);
  }

  private <T extends FlowNode> void removeFlowNodesOfTypeFromGraph(Class<T> FlowNodeType) {
    removeFlowNodesFromGraph(modelInstance.getModelElementsByType(FlowNodeType));
  }

  private void transformIntoGraph(Graph<String> graph, Set<FlowNode> currentNodes) {
    Set<String> alreadyProcessed = new HashSet<>();
    currentNodes.stream()
        .map(FlowNode::getId)
        .forEach(
            currentNode -> {
              LOG.debug("Adding Vertex {}", currentNode);
              graph.addVertex(currentNode);
            });

    boolean hasCurrentNodeSucceedingNodes = true;
    while (hasCurrentNodeSucceedingNodes) {
      for (FlowNode currentNode : currentNodes) {
        for (FlowNode succeedingNode : currentNode.getSucceedingNodes().list()) {
          // don't add same FlowNode multiple times
          if (!graph.contains(succeedingNode.getId())) {
            LOG.debug("Adding Vertex {}", succeedingNode.getId());
            graph.addVertex(succeedingNode.getId());
          }
          addEdge(currentNode.getId(), succeedingNode.getId());
        }
        alreadyProcessed.add(currentNode.getId());
      }

      Set<FlowNode> nextNodesCandidates =
          currentNodes.stream()
              .flatMap(node -> node.getSucceedingNodes().list().stream())
              .filter(flowNode -> !alreadyProcessed.contains(flowNode.getId()))
              .collect(Collectors.toSet());
      currentNodes = nextNodesCandidates;
      hasCurrentNodeSucceedingNodes = nextNodesCandidates.size() > 0;
    }
  }

  private Optional<String> getAssociatedMessageForBpmnElementId(String bpmnElementId) {
    Collection<EventDefinition> eventDefinitions =
        ((CatchEvent) modelInstance.getModelElementById(bpmnElementId)).getEventDefinitions();
    return eventDefinitions.stream()
        .filter(eventDefinition -> eventDefinition instanceof MessageEventDefinition)
        .map(
            eventDefinition ->
                String.valueOf(((MessageEventDefinition) eventDefinition).getMessage().getName()))
        .findAny();
  }

  private Path<String> findShortestPathForElementIds(
      String currentlyActiveBpmnElementId, List<String> targetBpmnIdCandidates) {
    Path<String> shortestPath =
        targetBpmnIdCandidates.stream()
            .map(
                targetCandidate -> {
                  LOG.info(
                      "Searching shortest path from {} to {}",
                      currentlyActiveBpmnElementId,
                      targetCandidate);
                  return processAsGraph
                      .algorithms()
                      .findShortestPath(currentlyActiveBpmnElementId, targetCandidate);
                })
            .filter(path -> path.size > 0)
            // Post-processing for boundary events: If the second node is a boundary event, we may
            // need to remove its direct predecessor
            .peek(
                path -> {
                  if (path.size() >= 2
                      && predecessorToBeRemoved.containsKey(path.get(1))
                      && predecessorToBeRemoved.get(path.get(1)).contains(path.get(0))) {
                    path.remove(0);
                  }
                })
            .min(Comparator.comparing(Path::size))
            .get();

    LOG.info(
        "Found shortest path: {}",
        StringUtils.arrayToDelimitedString(shortestPath.toArray(), "->"));
    return shortestPath;
  }

  private void addEdge(String sourceId, String targetId) {
    //        LOG.debug("Calling addEdge w/ {} -> {}", sourceId, targetId);
    List<String> sourceIds = determineSourceIdsFromReplacements(sourceId);
    List<String> targetIds = determineTargetIdsFromReplacements(targetId);

    sourceIds.stream()
        .filter(srcId -> !"-".equals(srcId))
        .forEach(
            srcId ->
                targetIds.stream()
                    .filter(trgId -> !"-".equals(trgId))
                    .forEach(
                        trgId -> {
                          if (processAsGraph.contains(srcId) && processAsGraph.contains(trgId)) {
                            LOG.debug("Adding edge: {} --> {}", srcId, trgId);
                            processAsGraph.addEdge(srcId, trgId);
                          }
                        }));
  }

  private List<String> determineSourceIdsFromReplacements(String sourceId) {
    if (replacements.containsKey(sourceId)) {
      Pair<List<String>, List<String>> replacementsOfSource = replacements.get(sourceId);
      return replacementsOfSource.getRight().stream()
          .map(this::determineSourceIdsFromReplacements)
          .flatMap(List::stream)
          .collect(Collectors.toList());
    }
    return List.of(sourceId);
  }

  private List<String> determineTargetIdsFromReplacements(String targetId) {
    if (replacements.containsKey(targetId)) {
      Pair<List<String>, List<String>> replacementsOfTarget = replacements.get(targetId);
      return replacementsOfTarget.getLeft().stream()
          .map(this::determineTargetIdsFromReplacements)
          .flatMap(List::stream)
          .collect(Collectors.toList());
    }
    return List.of(targetId);
  }

  private void addReplacement(String vertexToBeReplaced, Pair<String, String> replacement) {
    Pair<List<String>, List<String>> current =
        replacements.getOrDefault(
            vertexToBeReplaced, Pair.of(new ArrayList<>(), new ArrayList<>()));
    current.getLeft().add(replacement.getLeft());
    current.getRight().add(replacement.getRight());
    replacements.put(vertexToBeReplaced, current);
  }
}
