import RedisPubSub from '/imports/startup/server/redis';
import { check } from 'meteor/check';
import Polls from '/imports/api/polls';
import Logger from '/imports/startup/server/logger';
import { extractCredentials } from '/imports/api/common/server/helpers';

export default function publishVote(pollId, pollAnswerId,salt) {
  try {
    const REDIS_CONFIG = Meteor.settings.private.redis;
    const CHANNEL = REDIS_CONFIG.channels.toAkkaApps;
    const EVENT_NAME = 'RespondToPollReqMsg';
    const { meetingId, requesterUserId } = extractCredentials(this.userId);
    console.log("salt="+salt);
    check(meetingId, String);
    check(requesterUserId, String);
    check(pollAnswerId, Number);
    check(pollId, String);

    const allowedToVote = Polls.findOne({
      id: pollId,
      users: { $in: [requesterUserId] },
      meetingId,
    }, {
      fields: {
        users: 1,
      },
    });
    console.log(allowedToVote);
    //if (!allowedToVote) {
    //  Logger.info(`Poll User={${requesterUserId}} has already voted in PollId={${pollId}}`);
    //  return null;
    //}

    const selector = {
      users: requesterUserId,
      meetingId,
      'answers.id': pollAnswerId,
    };

    const payload = {
      requesterId: requesterUserId,
      pollId,
      questionId: 0,
      answerId: pollAnswerId,
    };

    /*
     We keep an array of people who were in the meeting at the time the poll
     was started. The poll is published to them only.
     Once they vote - their ID is removed and they cannot see the poll anymore
    */
    const modifier = {
      $pull: {
        users: requesterUserId,
      },
    };

    const numberAffected = Polls.update(selector, modifier);
    console.log('numberAffected=>'+numberAffected);
    //if (numberAffected) {
      Logger.info(`Removed responded user=${requesterUserId} from poll (meetingId: ${meetingId}, pollId: ${pollId}!)`);

      RedisPubSub.publishUserMessage(CHANNEL, EVENT_NAME, meetingId, requesterUserId, payload);
    //}
  } catch (err) {
    Logger.error(`Exception while invoking method publishVote ${err.stack}`);
  }
}
