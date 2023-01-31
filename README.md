[![Community badge: Incubating](https://img.shields.io/badge/Lifecycle-Incubating-blue)](https://github.com/Camunda-Community-Hub/community/blob/main/extension-lifecycle.md#incubating-)
[![Community extension badge](https://img.shields.io/badge/Community%20Extension-An%20open%20source%20community%20maintained%20project-FF4700)](https://github.com/camunda-community-hub/community)

# Motivation
With BPMN we describe a well-defined structured flow in which a process should be executed.
It is possible to model a process which only contains Intermediate Message Catch Events to display the current state of a process.
This only works out-of-the box though, if all required Messages arrive in the modeled order.

The real world, however, sometimes is messy and Messages might arrive out-of-order. This project allows handling of such scenarios.

# Usage
- Process has to be started via `ZeebeService.startProcessVia...()`.
- Subsequent messages have to be sent via `ZeebeService.sendArbitraryMessage()`.

# Behaviour
- If the process is currently waiting for the Message, it will get correlated.
- If the process is waiting for a Message and receives a subsequent message with more recent timestamp, all messages on the way to the subsequent message will get send (with `synthetic=true` and a timestamp 1 millisecond earlier), so the process will reach the wanted state. The shortest path will be used.
- If the process is waiting for a message and receives a Message with an earlier timestamp, it won't get processed.
- Loops are possible.
- Event-based Subprocesses will get sent directly.

# Prerequisites
- Possibility to request state from Zeebe: Process must contain an event-based SubProcess with
  - MessageStartMessage of type `mc.syncMessage`
  - MessageEndEvent with TaskDefinition of type `mc.syncTaskTypePrefix`+`id`
  - TODO: add example
  - TODO: In the future this might also be possible to do via Operate API (Operate's eventual consistency has to be considered, though)
- The process must be modeled so that the same Message will always lead to the same succeeding Message Event (exception: event-based subprocesses)
  - TODO: add example

# Configuration
Properties `mc.path`, `mc.syncMessage`, `mc.syncTaskTypePrefix` and have to be set.
Optionally `mc.messagesProcessVar` can be set (defaults to 'messages').

## Example
```
mc.path=src/main/resources/models/my-message-driven-process.bpmn
mc.syncMessage=Msg_ProcessState
mc.syncTaskTypePrefix=processState_
mc.messagesProcessVar=messages
```

# Limitations
- Process can currently only move forward
  - TODO: Possible Improvement: Utilize process instance modification (https://docs.camunda.io/docs/components/operate/userguide/process-instance-modification/) based on timestamp to move back in process.

