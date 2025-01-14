package org.bigbluebutton.core.apps.polls

import org.bigbluebutton.common2.domain.SimplePollResultOutVO
import org.bigbluebutton.common2.msgs._
import org.bigbluebutton.core.bus.MessageBus
import org.bigbluebutton.core.models.Polls
import org.bigbluebutton.core.running.{ LiveMeeting }
import org.bigbluebutton.core.models.Users2x

trait RespondToPollReqMsgHdlr {
  this: PollApp2x =>

  def handle(msg: RespondToPollReqMsg, liveMeeting: LiveMeeting, bus: MessageBus): Unit = {

    def broadcastPollUpdatedEvent(msg: RespondToPollReqMsg, pollId: String, poll: SimplePollResultOutVO): Unit = {
      val routing = Routing.addMsgToClientRouting(MessageTypes.BROADCAST_TO_MEETING, liveMeeting.props.meetingProp.intId, msg.header.userId)
      val envelope = BbbCoreEnvelope(PollUpdatedEvtMsg.NAME, routing)
      val header = BbbClientMsgHeader(PollUpdatedEvtMsg.NAME, liveMeeting.props.meetingProp.intId, msg.header.userId)

      val body = PollUpdatedEvtMsgBody(pollId, poll)
      val event = PollUpdatedEvtMsg(header, body)
      val msgEvent = BbbCommonEnvCoreMsg(envelope, event)
      bus.outGW.send(msgEvent)
    }

    def broadcastUserRespondedToPollRecordMsg(msg: RespondToPollReqMsg, pollId: String, answerId: Int, answer: String, isSecret: Boolean): Unit = {
      val routing = Routing.addMsgToClientRouting(MessageTypes.BROADCAST_TO_MEETING, liveMeeting.props.meetingProp.intId, msg.header.userId)
      val envelope = BbbCoreEnvelope(UserRespondedToPollRecordMsg.NAME, routing)
      val header = BbbClientMsgHeader(UserRespondedToPollRecordMsg.NAME, liveMeeting.props.meetingProp.intId, msg.header.userId)

      val body = UserRespondedToPollRecordMsgBody(pollId, answerId, answer, isSecret)
      val event = UserRespondedToPollRecordMsg(header, body)
      val msgEvent = BbbCommonEnvCoreMsg(envelope, event)
      bus.outGW.send(msgEvent)
    }

    def broadcastUserRespondedToPollRespMsg(msg: RespondToPollReqMsg, pollId: String, answerId: Int, sendToId: String): Unit = {
      val routing = Routing.addMsgToClientRouting(MessageTypes.DIRECT, liveMeeting.props.meetingProp.intId, sendToId)
      val envelope = BbbCoreEnvelope(UserRespondedToPollRespMsg.NAME, routing)
      val header = BbbClientMsgHeader(UserRespondedToPollRespMsg.NAME, liveMeeting.props.meetingProp.intId, sendToId)

      val body = UserRespondedToPollRespMsgBody(pollId, msg.header.userId, answerId)
      val event = UserRespondedToPollRespMsg(header, body)
      val msgEvent = BbbCommonEnvCoreMsg(envelope, event)
      bus.outGW.send(msgEvent)
    }

    for {
      (pollId: String, updatedPoll: SimplePollResultOutVO) <- Polls.handleRespondToPollReqMsg(msg.header.userId, msg.body.pollId,
        msg.body.questionId, msg.body.answerId, liveMeeting)
    } yield {
      log.debug("updatedPoll  {}", updatedPoll)
      broadcastPollUpdatedEvent(msg, pollId, updatedPoll)
      for {
        poll <- Polls.getPoll(pollId, liveMeeting.polls)
      } yield {
        val answerText = poll.questions(0).answers.get(msg.body.answerId).key
        broadcastUserRespondedToPollRecordMsg(msg, pollId, msg.body.answerId, answerText, poll.isSecret)
      }

      for {
        presenter <- Users2x.findPresenter(liveMeeting.users2x)
      } yield {
        broadcastUserRespondedToPollRespMsg(msg, pollId, msg.body.answerId, presenter.intId)
      }
    }
  }
}
