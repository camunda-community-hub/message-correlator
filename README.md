[![Community badge: Incubating](https://img.shields.io/badge/Lifecycle-Incubating-blue)](https://github.com/Camunda-Community-Hub/community/blob/main/extension-lifecycle.md#incubating-)
[![Community extension badge](https://img.shields.io/badge/Community%20Extension-An%20open%20source%20community%20maintained%20project-FF4700)](https://github.com/camunda-community-hub/community)

# Motivation
With BPMN we describe a well-defined structured flow in which a process should be executed.</br>
It is possible to model a process which only contains Intermediate Message Catch Events to display the current state of a process.</br>
This only works out-of-the box though, if all required Messages arrive eventually (ideally in the modeled order, even though that is not necessary, as Zeebe allows [Message Buffering](https://docs.camunda.io/docs/components/concepts/messages/#message-buffering)).</br>

The real world, however, can be messy and <u>Messages might get lost</u>
This project allows handling of scenarios in which Messages get lost and moves the process directly to the latest Message Event, even if Events in between are (still) missing.</br>

This project originated from a use case in which it was important that an incoming Message gets correlated directly when it arrives, as the process variables, that got passed with it, had to be updated immediately.</br>

# Usage
- Process has to be started via `ZeebeService.startProcessViaMessage()`.
  -  to-be-correlated Messages have to be added by Client to processVars as a Map<String, MessageBody> with variableName=`message-correlator.messagesProcessVar`
- Subsequent messages have to be sent via `ZeebeService.sendArbitraryMessage()`.
- see src/test/java/org/camunda/community/messagecorrelator/ZeebeServiceTest and src/test/resources/client_example

# Behaviour
- If the process is currently waiting for the Message, it will get correlated.
- If the process is waiting for a Message and receives a subsequent message with more recent timestamp, all messages on the way to the subsequent message will get send (with `synthetic=true` and a timestamp 1 millisecond earlier), so the process will reach the wanted state. The shortest path will be used.
- If the process is waiting for a message and receives a Message with an earlier timestamp, it won't get processed.
- Loops are possible.
- Event-based Subprocesses will get sent directly.

# Prerequisites
- Possibility to request state from Zeebe: Process must contain an event-based SubProcess with
  - MessageStartMessage of type `message-correlator.syncMessage`
  - MessageEndEvent with TaskDefinition of type `message-correlator.syncTaskTypePrefix`+`id`
- The process must be modeled so that the same Message will always lead to the same succeeding Message Event (exception: event-based subprocesses)

## Example Process Model
![example_process](example_process.png)</br>
see also src/test/resources/client_example/example_process.bpmn

# Configuration
Properties file `application.properties` has to exist.</br>
Properties `message-correlator.path`, `message-correlator.syncMessage`, `message-correlator.syncTaskTypePrefix` and have to be set.</br>
Optionally `message-correlator.messagesProcessVar` can be set (defaults to 'messages').

## Example Configuration
```
message-correlator.path=src/test/resources/client_example/example_process.bpmn
message-correlator.syncMessage=Msg_ProcessState
message-correlator.syncTaskTypePrefix=processState_
message-correlator.messagesProcessVar=messages
```
see also src/test/resources/client_example/example_application.properties

## Why we don't use Operate to query the current state
When messages overtake each other due to a race condition, they will likely arrive within milliseconds of each other and then the eventual consistency will likely mean that the Operate API delivers and outdated result, thus leading to more synthetic messages. Operate is only an option, when the messages arrive with sufficient distance from each other, but in that case the workload of the engine is likely not that high that the direct queries to the process need to be prevented (see https://github.com/camunda-community-hub/message-correlator/issues/15#issuecomment-1423830436) </br>

## Why we don't use Process Instance Modification
[Process Instance Modification](https://docs.camunda.io/docs/components/operate/userguide/process-instance-modification/) allows us to move the Token within the process. When doing so the modification is clearly visible in Operate.</br>
Process Instance Modification is currently not possible on all BPMN Elements (e.g. it is not possible to modify Process Instances within a Multi-Instance, and if an Intermediate Message Catch Event is behind an event-based Gateway, the event-based Gateway has to be activated, before the Message can be correlated) and it also breaks the history and Heatmap within Optimize.</br>
See https://github.com/camunda-community-hub/message-correlator/issues/16#issuecomment-1423821925 for further information.
