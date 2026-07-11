import 'package:flutter_test/flutter_test.dart';
import 'package:leadora_mobile/features/deal/data/deal_models.dart';

void main() {
  group('DealStage.fromWire', () {
    test('maps every backend enum value', () {
      expect(DealStage.fromWire('PROSPECTING'), DealStage.prospecting);
      expect(DealStage.fromWire('QUALIFICATION'), DealStage.qualification);
      expect(DealStage.fromWire('PROPOSAL'), DealStage.proposal);
      expect(DealStage.fromWire('NEGOTIATION'), DealStage.negotiation);
      expect(DealStage.fromWire('CLOSED_WON'), DealStage.closedWon);
      expect(DealStage.fromWire('CLOSED_LOST'), DealStage.closedLost);
    });

    test('degrades to null on an unknown or absent value', () {
      expect(DealStage.fromWire('SOME_FUTURE_STAGE'), isNull);
      expect(DealStage.fromWire(null), isNull);
      // The display label must never be mistaken for a code.
      expect(DealStage.fromWire('Negotiation'), isNull);
    });
  });

  group('DealStage funnel', () {
    test('next walks forward and stops at both terminal stages', () {
      expect(DealStage.prospecting.next, DealStage.qualification);
      expect(DealStage.negotiation.next, DealStage.closedWon);
      expect(DealStage.closedWon.next, isNull);
      expect(DealStage.closedLost.next, isNull);
    });

    test('both terminal stages share the last funnel slot', () {
      expect(DealStage.closedWon.order, DealStage.closedLost.order);
      expect(DealStage.closedWon.isTerminal, isTrue);
      expect(DealStage.closedLost.isTerminal, isTrue);
      expect(DealStage.proposal.isTerminal, isFalse);
    });
  });

  group('Deal.fromJson', () {
    test('prefers stageCode and keeps the label for display', () {
      final deal = Deal.fromJson({
        'id': 'd1',
        'title': 'Corporate block',
        'status': 'active',
        'stage': 'Negotiation',
        'stageCode': 'NEGOTIATION',
      });

      expect(deal.stage, DealStage.negotiation);
      expect(deal.stageLabel, 'Negotiation');
      expect(deal.displayStage, 'Negotiation');
      expect(deal.status, DealStatus.active);
    });

    test(
      'a lost deal is not mistaken for a won one when only the label is read',
      () {
        // The backend renders CLOSED_LOST as "Confirmed" — the same label it
        // uses for CLOSED_WON. Only stageCode can tell them apart.
        final lost = Deal.fromJson({
          'id': 'd2',
          'title': 'Lost block',
          'status': 'lost',
          'stage': 'Confirmed',
          'stageCode': 'CLOSED_LOST',
        });

        expect(lost.stage, DealStage.closedLost);
        expect(lost.displayStage, 'Lost');
        expect(lost.status, DealStatus.lost);
      },
    );

    test('falls back to the raw label when stageCode is unknown', () {
      final deal = Deal.fromJson({
        'id': 'd3',
        'title': 'Future stage',
        'status': 'active',
        'stage': 'Onboarding',
        'stageCode': 'ONBOARDING',
      });

      expect(deal.stage, isNull);
      expect(deal.displayStage, 'Onboarding');
    });

    test('falls back to a dash when neither field is present', () {
      final deal = Deal.fromJson({
        'id': 'd4',
        'title': 'Bare deal',
        'status': 'active',
      });

      expect(deal.displayStage, '—');
    });
  });

  group('DealPayload.toJson', () {
    test('sends the wire enum, never the display label', () {
      final json = DealPayload(
        title: 'Corporate block',
        contactName: 'Huong Pham',
        stage: DealStage.closedLost,
        status: DealStatus.lost,
      ).toJson();

      expect(json['stage'], 'CLOSED_LOST');
      expect(json['status'], 'lost');
    });

    test('omits null fields so PUT stays a partial update', () {
      final json = DealPayload(
        title: 'Corporate block',
        contactName: 'Huong Pham',
      ).toJson();

      expect(json.keys, unorderedEquals(['title', 'contactName']));
      expect(json.containsKey('notes'), isFalse);
      expect(json.containsKey('stage'), isFalse);
    });

    test('expectedClose serializes as a plain LocalDate, not an instant', () {
      final json = DealPayload(
        title: 'Corporate block',
        contactName: 'Huong Pham',
        // An evening in UTC+7 would roll back a day if sent as a UTC instant.
        expectedClose: DateTime(2026, 3, 9, 23, 30),
      ).toJson();

      expect(json['expectedClose'], '2026-03-09');
    });
  });
}
