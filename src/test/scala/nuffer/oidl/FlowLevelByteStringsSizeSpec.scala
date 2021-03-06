package nuffer.oidl

import akka.stream.scaladsl.{Sink, Source}
import akka.stream.testkit.{StreamSpec, TestPublisher, TestSubscriber}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString

import scala.collection.immutable._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class FlowLevelByteStringsSizeSpec extends StreamSpec {

  val settings = ActorMaterializerSettings(system)
    .withInputBuffer(initialSize = 1, maxSize = 1)

  implicit val materializer = ActorMaterializer(settings)

  "LevelByteStringsSize" must {
    "pass 2, 2 through 3 as 3, 1" in {
      val future: Future[Seq[ByteString]] = Source(Seq(ByteString(1, 2), ByteString(3, 4)))
        .via(LevelByteStringsSize(3))
        .grouped(101)
        .runWith(Sink.head)

      Await.result(future, 3.seconds) should be(Vector(ByteString(1, 2, 3), ByteString(4)))
    }

    "pass 2, 2 through 1 as 4x1" in {
      val future: Future[Seq[ByteString]] = Source(Seq(ByteString(1, 2), ByteString(3, 4)))
        .via(LevelByteStringsSize(1))
        .grouped(101)
        .runWith(Sink.head)

      Await.result(future, 3.seconds) should be(Vector(ByteString(1), ByteString(2), ByteString(3), ByteString(4)))
    }

    "pass 2, 2 through 2 as 2, 2" in {
      val future: Future[Seq[ByteString]] = Source(Seq(ByteString(1, 2), ByteString(3, 4)))
        .via(LevelByteStringsSize(2))
        .grouped(101)
        .runWith(Sink.head)

      Await.result(future, 3.seconds) should be(Vector(ByteString(1, 2), ByteString(3, 4)))
    }

    "pass 2, 2 through 4 as 4" in {
      val future: Future[Seq[ByteString]] = Source(Seq(ByteString(1, 2), ByteString(3, 4)))
        .via(LevelByteStringsSize(4))
        .grouped(101)
        .runWith(Sink.head)

      Await.result(future, 3.seconds) should be(Vector(ByteString(1, 2, 3, 4)))
    }

    "onPull, isClosed(in)" in {
      val publisher = TestPublisher.probe[ByteString]()
      val subscriber = TestSubscriber.manualProbe[ByteString]()
      Source.fromPublisher(publisher)
        .via(LevelByteStringsSize(1))
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()
      publisher.sendNext(ByteString(1))
      publisher.sendComplete()

      sub.request(1)
      subscriber.expectNext(ByteString(1))
      subscriber.expectComplete()
    }

    "onPull, isClosed(in), buffer size 2" in {
      val publisher = TestPublisher.probe[ByteString]()
      val subscriber = TestSubscriber.manualProbe[ByteString]()
      Source.fromPublisher(publisher)
        .via(LevelByteStringsSize(2))
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()
      publisher.sendNext(ByteString(1))
      publisher.sendNext(ByteString(2))
      publisher.sendComplete()

      sub.request(2)
      subscriber.expectNext(ByteString(1, 2))
      subscriber.expectComplete()
    }

    "onPull, isClosed(in), buffer size 1, 2 items" in {
      val publisher = TestPublisher.probe[ByteString]()
      val subscriber = TestSubscriber.manualProbe[ByteString]()
      Source.fromPublisher(publisher)
        .via(LevelByteStringsSize(1))
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()
      publisher.sendNext(ByteString(1))
      publisher.sendNext(ByteString(2))
      publisher.sendComplete()

      sub.request(2)
      subscriber.expectNext(ByteString(1))
      subscriber.expectNext(ByteString(2))
      subscriber.expectComplete()
    }

    "3 items, buffer size 2" in {
      val publisher = TestPublisher.probe[ByteString]()
      val subscriber = TestSubscriber.manualProbe[ByteString]()
      Source.fromPublisher(publisher)
        .via(LevelByteStringsSize(2))
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()
      publisher.sendNext(ByteString(1))
      publisher.sendNext(ByteString(2))
      publisher.sendNext(ByteString(3))
      publisher.sendComplete()

      sub.request(3)
      subscriber.expectNext(ByteString(1, 2))
      subscriber.expectNext(ByteString(3))
      subscriber.expectComplete()
    }

    "5 items, buffer size 2" in {
      val publisher = TestPublisher.probe[ByteString]()
      val subscriber = TestSubscriber.manualProbe[ByteString]()
      Source.fromPublisher(publisher)
        .via(LevelByteStringsSize(2))
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()
      publisher.sendNext(ByteString(1))
      publisher.sendNext(ByteString(2))
      sub.request(1)
      subscriber.expectNext(ByteString(1, 2))
      publisher.sendNext(ByteString(3))
      publisher.sendNext(ByteString(4))
      sub.request(1)
      subscriber.expectNext(ByteString(3, 4))

      publisher.sendNext(ByteString(5))
      publisher.sendComplete()

      sub.request(2)
      subscriber.expectNext(ByteString(5))
      subscriber.expectComplete()
    }

    "level chunksize 2" in {
      val future: Future[Seq[ByteString]] = Source.repeat(ByteString(1))
        .via(LevelByteStringsSize(2))
        .grouped(101)
        .runWith(Sink.head)

      val result = Await.result(future, 3.seconds)
      result should be((1 to 101).map(_ => {
        ByteString.fromInts((1 to 2).map(_ => 1): _*)
      }))
    }

    "multiple sizes chained 2" in {
      val future: Future[Seq[ByteString]] = Source.repeat(ByteString(1))
        .via(LevelByteStringsSize(1))
        .via(LevelByteStringsSize(2))
        .grouped(101)
        .runWith(Sink.head)

      val result = Await.result(future, 3.seconds)
      result should be((1 to 101).map(_ => {
        ByteString.fromInts((1 to 2).map(_ => 1): _*)
      }))
    }

    "multiple sizes chained" in {
      val future: Future[Seq[ByteString]] = Source.repeat(ByteString(1))
        .via(LevelByteStringsSize(1))
        .via(LevelByteStringsSize(2))
        .via(LevelByteStringsSize(3))
        .via(LevelByteStringsSize(4))
        .via(LevelByteStringsSize(5))
        .via(LevelByteStringsSize(8))
        .via(LevelByteStringsSize(13))
        .via(LevelByteStringsSize(17))
        .via(LevelByteStringsSize(13))
        .via(LevelByteStringsSize(11))
        .via(LevelByteStringsSize(7))
        .via(LevelByteStringsSize(5))
        .via(LevelByteStringsSize(3))
        .via(LevelByteStringsSize(127))
        .via(LevelByteStringsSize(2))
        .via(LevelByteStringsSize(4))
        .via(LevelByteStringsSize(8))
        .via(LevelByteStringsSize(16))
        .via(LevelByteStringsSize(8))
        .via(LevelByteStringsSize(4))
        .via(LevelByteStringsSize(2))
        .via(LevelByteStringsSize(7))
        .via(LevelByteStringsSize(101))
        .grouped(101)
        .runWith(Sink.head)

      val result = Await.result(future, 3.seconds)
      result should be((1 to 101).map(_ => {
        ByteString.fromInts((1 to 101).map(_ => 1): _*)
      }))
    }

    "multiple sizes chained, partial last item" in {
      val future: Future[Seq[ByteString]] = Source.repeat(ByteString(1))
        .take(101 * 101 - 1)
        .via(LevelByteStringsSize(1))
        .via(LevelByteStringsSize(2))
        .via(LevelByteStringsSize(3))
        .via(LevelByteStringsSize(4))
        .via(LevelByteStringsSize(5))
        .via(LevelByteStringsSize(8))
        .via(LevelByteStringsSize(13))
        .via(LevelByteStringsSize(17))
        .via(LevelByteStringsSize(13))
        .via(LevelByteStringsSize(11))
        .via(LevelByteStringsSize(7))
        .via(LevelByteStringsSize(5))
        .via(LevelByteStringsSize(3))
        .via(LevelByteStringsSize(127))
        .via(LevelByteStringsSize(2))
        .via(LevelByteStringsSize(4))
        .via(LevelByteStringsSize(8))
        .via(LevelByteStringsSize(16))
        .via(LevelByteStringsSize(8))
        .via(LevelByteStringsSize(4))
        .via(LevelByteStringsSize(2))
        .via(LevelByteStringsSize(7))
        .via(LevelByteStringsSize(101))
        .grouped(101)
        .runWith(Sink.head)

      val result = Await.result(future, 3.seconds)
      result should be((1 to 101).map(x => {
        if (x < 101)
          ByteString.fromInts((1 to 101).map(_ => 1): _*)
        else
          ByteString.fromInts((1 to 100).map(_ => 1): _*)
      }))
    }

    "empty source" in {
      val future: Future[Seq[ByteString]] = Source.empty
        .via(LevelByteStringsSize(2))
        .runWith(Sink.seq)

      val result = Await.result(future, 3.seconds)
      result should be(Vector())
    }

    "empty ByteString" in {
      val future: Future[Seq[ByteString]] = Source.single(ByteString())
        .via(LevelByteStringsSize(2))
        .runWith(Sink.seq)

      val result = Await.result(future, 3.seconds)
      result should be(Vector())
    }
  }
}
