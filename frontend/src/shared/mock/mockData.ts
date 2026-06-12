export interface Lead {
  id: string;
  name: string;
  contactName: string;
  email: string;
  phone: string;
  company: string;
  source: string;
  status: 'new' | 'contacted' | 'qualified' | 'lost';
  owner: string;
  value: number;
  createdAt: string;
  notes: string;
}

export interface Deal {
  id: string;
  title: string;
  contactName: string;
  email: string;
  phone: string;
  stage: 'Inquiry' | 'Site Visit' | 'Proposal' | 'Negotiation' | 'Contract' | 'Confirmed';
  value: number;
  probability: number;
  owner: string;
  expectedClose: string;
  status: 'active' | 'won' | 'lost';
  createdAt: string;
}

export interface FollowUpTask {
  id: string;
  title: string;
  description: string;
  dueDate: string;
  priority: 'low' | 'medium' | 'high';
  status: 'pending' | 'completed' | 'overdue';
  linkedEntityId?: string;
  linkedEntityName?: string;
  assignee: string;
}

export interface CustomerProfile {
  id: string;
  name: string;
  email: string;
  phone: string;
  company: string;
  lastInteractionDate: string;
  totalDealValue: number;
  notes: string;
}

export interface InteractionTimeline {
  id: string;
  type: 'call' | 'email' | 'meeting' | 'note';
  date: string;
  description: string;
  agentName: string;
  linkedName: string;
}

export interface Quotation {
  id: string;
  quoteNo: string;
  contactName: string;
  dealName: string;
  amount: number;
  status: 'draft' | 'sent' | 'accepted' | 'expired';
  sentDate: string;
  expiryDate: string;
}

export interface Notification {
  id: string;
  message: string;
  type: 'alert' | 'info' | 'success';
  time: string;
  read: boolean;
}

export interface Reminder {
  id: string;
  title: string;
  dueDate: string;
  priority: 'low' | 'medium' | 'high';
  linkedEntityName: string;
  status: 'active' | 'completed';
}

export interface SLA {
  id: string;
  name: string;
  module: string;
  thresholdHours: number;
  compliancePct: number;
  status: 'active' | 'inactive';
}

export interface BookingConfirmation {
  id: string;
  bookingNo: string;
  guestName: string;
  roomType: string;
  checkIn: string;
  checkOut: string;
  amount: number;
  status: 'pending' | 'confirmed' | 'cancelled';
}

export interface ReservationStatus {
  id: string;
  reservationNo: string;
  guestName: string;
  roomType: string;
  status: 'Checked-In' | 'Checked-Out' | 'Confirmed' | 'No-Show';
  channel: 'Direct' | 'OTA' | 'Agent';
  createdDate: string;
}

export interface OperationalHandover {
  id: string;
  handoverNo: string;
  fromDept: string;
  toDept: string;
  date: string;
  status: 'pending' | 'completed';
  notes: string;
}

export interface DepositPayment {
  id: string;
  paymentNo: string;
  guestName: string;
  amount: number;
  method: 'Card' | 'Transfer' | 'Cash';
  status: 'paid' | 'pending' | 'refunded';
  date: string;
}

export interface FrontOfficeHandover {
  id: string;
  shift: 'Morning' | 'Afternoon' | 'Night';
  agentName: string;
  date: string;
  status: 'completed' | 'pending';
  notes: string;
}

export interface UserAccess {
  id: string;
  name: string;
  email: string;
  role: 'ADMIN' | 'MANAGER' | 'SALES' | 'FRONT_OFFICE' | 'STAFF';
  status: 'active' | 'inactive';
  lastLogin: string;
}

export interface CustomerFeedback {
  id: string;
  feedbackNo: string;
  guestName: string;
  rating: number;
  category: 'Service' | 'Room' | 'Food' | 'Facilities';
  comment: string;
  date: string;
}

// Global state emulation helper
class MockDatabase {
  leads: Lead[] = [
    { id: "L-101", name: "Corporate Retreat 2026", contactName: "Alice Jenkins", email: "alice.j@techcorp.com", phone: "+1 555-0192", company: "TechCorp Inc.", source: "Website Inquiry", status: "new", owner: "John Doe", value: 12500, createdAt: "2026-06-10", notes: "Requires 40 deluxe rooms and conference hall for 3 days." },
    { id: "L-102", name: "Annual Gala Dinner", contactName: "Robert Chen", email: "rchen@galaevents.org", phone: "+1 555-0143", company: "Gala Events Org", source: "Referral", status: "contacted", owner: "Sarah Connor", value: 8500, createdAt: "2026-06-08", notes: "Catering for 150 guests. Prefers outdoor garden setup." },
    { id: "L-103", name: "Wedding Reception - Miller", contactName: "Emily Miller", email: "emily.miller@gmail.com", phone: "+1 555-0187", company: "Individual", source: "Social Media", status: "qualified", owner: "John Doe", value: 24000, createdAt: "2026-06-05", notes: "Looking at Grand Ballroom. Needs accommodation for 50 out-of-town guests." },
    { id: "L-104", name: "Executive Board Meeting", contactName: "Marcus Vance", email: "m.vance@financeholdings.com", phone: "+1 555-0112", company: "Finance Holdings", source: "Cold Call", status: "new", owner: "Sarah Connor", value: 4500, createdAt: "2026-06-11", notes: "VIP amenities requested. 12 board members." },
    { id: "L-105", name: "Summer Family Reunion", contactName: "Diana Ross", email: "dross@familynet.org", phone: "+1 555-0130", company: "Ross Family", source: "Website Inquiry", status: "lost", owner: "John Doe", value: 6200, createdAt: "2026-06-01", notes: "Lost to competitor due to budget constraints." }
  ];

  deals: Deal[] = [
    { id: "D-201", title: "TechCorp Annual Conference", contactName: "Alice Jenkins", email: "alice.j@techcorp.com", phone: "+1 555-0192", stage: "Proposal", value: 12500, probability: 60, owner: "John Doe", expectedClose: "2026-07-15", status: "active", createdAt: "2026-06-10" },
    { id: "D-202", title: "Miller Wedding Booking", contactName: "Emily Miller", email: "emily.miller@gmail.com", phone: "+1 555-0187", stage: "Negotiation", value: 24000, probability: 80, owner: "John Doe", expectedClose: "2026-06-30", status: "active", createdAt: "2026-06-05" },
    { id: "D-203", title: "Global Travel Tour Group Jul", contactName: "Kazu Tanaka", email: "k.tanaka@globaltravel.co.jp", phone: "+81 3-5555-0144", stage: "Site Visit", value: 35000, probability: 40, owner: "Sarah Connor", expectedClose: "2026-08-01", status: "active", createdAt: "2026-05-20" },
    { id: "D-204", title: "Pharma Product Launch", contactName: "Dr. Helen Hunt", email: "hhunt@pharmamed.com", phone: "+1 555-0129", stage: "Inquiry", value: 18000, probability: 10, owner: "Sarah Connor", expectedClose: "2026-09-10", status: "active", createdAt: "2026-06-12" },
    { id: "D-205", title: "Yoga Retreat Weekend Sept", contactName: "Sari Patel", email: "sari.yogi@mindbody.com", phone: "+1 555-0176", stage: "Contract", value: 9500, probability: 95, owner: "John Doe", expectedClose: "2026-06-25", status: "active", createdAt: "2026-05-15" },
    { id: "D-206", title: "Rotary Club Dinner", contactName: "Bill Gateson", email: "bgateson@rotary3010.org", phone: "+1 555-0100", stage: "Confirmed", value: 5500, probability: 100, owner: "Sarah Connor", expectedClose: "2026-06-10", status: "won", createdAt: "2026-06-02" }
  ];

  tasks: FollowUpTask[] = [
    { id: "T-301", title: "Send Banquet Menu Proposal", description: "Email menu options A, B & C to Emily Miller for the wedding group.", dueDate: "2026-06-12T17:00:00Z", priority: "high", status: "pending", linkedEntityId: "D-202", linkedEntityName: "Miller Wedding Booking", assignee: "John Doe" },
    { id: "T-302", title: "Follow up on TechCorp Proposal", description: "Call Alice to discuss the conference hall rates.", dueDate: "2026-06-13T10:00:00Z", priority: "medium", status: "pending", linkedEntityId: "D-201", linkedEntityName: "TechCorp Annual Conference", assignee: "John Doe" },
    { id: "T-303", title: "Conduct Site Inspection", description: "Show Kazu Tanaka around the resort suites and spa features.", dueDate: "2026-06-12T14:00:00Z", priority: "high", status: "completed", linkedEntityId: "D-203", linkedEntityName: "Global Travel Tour Group Jul", assignee: "Sarah Connor" },
    { id: "T-304", title: "Draft Contract Addendum", description: "Add airport transfer clauses for the Yoga Retreat group.", dueDate: "2026-06-15T09:00:00Z", priority: "low", status: "pending", linkedEntityId: "D-205", linkedEntityName: "Yoga Retreat Weekend Sept", assignee: "John Doe" },
    { id: "T-305", title: "Review Feedback - Rotary Club", description: "Follow up with F&B team regarding comments on dessert service.", dueDate: "2026-06-11T12:00:00Z", priority: "medium", status: "overdue", linkedEntityId: "D-206", linkedEntityName: "Rotary Club Dinner", assignee: "Sarah Connor" }
  ];

  contacts: CustomerProfile[] = [
    { id: "C-401", name: "Alice Jenkins", email: "alice.j@techcorp.com", phone: "+1 555-0192", company: "TechCorp Inc.", lastInteractionDate: "2026-06-10", totalDealValue: 12500, notes: "Key corporate account organizer. Prefers email communication." },
    { id: "C-402", name: "Emily Miller", email: "emily.miller@gmail.com", phone: "+1 555-0187", company: "Individual", lastInteractionDate: "2026-06-12", totalDealValue: 24000, notes: "Bride-to-be. Very detail oriented." },
    { id: "C-403", name: "Kazu Tanaka", email: "k.tanaka@globaltravel.co.jp", phone: "+81 3-5555-0144", company: "Global Travel Japan", lastInteractionDate: "2026-06-12", totalDealValue: 35000, notes: "Leads seasonal tours. Interested in custom group rates." },
    { id: "C-404", name: "Dr. Helen Hunt", email: "hhunt@pharmamed.com", phone: "+1 555-0129", company: "PharmaMed Labs", lastInteractionDate: "2026-06-12", totalDealValue: 18000, notes: "Organizes medical conferences. Needs absolute privacy and top tier AV." }
  ];

  interactions: InteractionTimeline[] = [
    { id: "I-501", type: "call", date: "2026-06-12 11:30", description: "Called Alice regarding the tech requirements in conference hall. Verified standard speed.", agentName: "John Doe", linkedName: "TechCorp Annual Conference" },
    { id: "I-502", type: "email", date: "2026-06-11 09:15", description: "Sent initial draft proposal with rate discount for group rooms.", agentName: "John Doe", linkedName: "Yoga Retreat Weekend Sept" },
    { id: "I-503", type: "meeting", date: "2026-06-10 14:00", description: "Site tour conducted for Rotary dinner planner. Showed banquet hall A.", agentName: "Sarah Connor", linkedName: "Rotary Club Dinner" },
    { id: "I-504", type: "note", date: "2026-06-08 16:45", description: "Bride requested extra vegan options for F&B package.", agentName: "John Doe", linkedName: "Miller Wedding Booking" }
  ];

  quotations: Quotation[] = [
    { id: "Q-601", quoteNo: "QT-2026-0043", contactName: "Emily Miller", dealName: "Miller Wedding Booking", amount: 24000, status: "sent", sentDate: "2026-06-06", expiryDate: "2026-06-20" },
    { id: "Q-602", quoteNo: "QT-2026-0044", contactName: "Alice Jenkins", dealName: "TechCorp Annual Conference", amount: 13200, status: "draft", sentDate: "2026-06-11", expiryDate: "2026-06-25" },
    { id: "Q-603", quoteNo: "QT-2026-0039", contactName: "Bill Gateson", dealName: "Rotary Club Dinner", amount: 5500, status: "accepted", sentDate: "2026-06-02", expiryDate: "2026-06-09" }
  ];

  notifications: Notification[] = [
    { id: "N-701", message: "New Lead 'Executive Board Meeting' assigned to Sarah Connor", type: "info", time: "10 mins ago", read: false },
    { id: "N-702", message: "SLA alert: 'Miller Wedding Booking' waiting for proposal response > 24h", type: "alert", time: "1 hour ago", read: false },
    { id: "N-703", message: "Quotation QT-2026-0039 accepted by Rotary Club", type: "success", time: "2 hours ago", read: true }
  ];

  reminders: Reminder[] = [
    { id: "R-801", title: "Deposit Due Reminder", dueDate: "2026-06-14", priority: "high", linkedEntityName: "Yoga Retreat Weekend Sept", status: "active" },
    { id: "R-802", title: "Room Block Release", dueDate: "2026-06-20", priority: "medium", linkedEntityName: "Global Travel Tour Group Jul", status: "active" }
  ];

  slas: SLA[] = [
    { id: "SLA-901", name: "First Response (New Lead)", module: "Leads", thresholdHours: 2, compliancePct: 92.5, status: "active" },
    { id: "SLA-902", name: "Proposal Generation", module: "Deals", thresholdHours: 24, compliancePct: 88.0, status: "active" },
    { id: "SLA-903", name: "Contract Execution", module: "Deals", thresholdHours: 48, compliancePct: 95.1, status: "active" }
  ];

  bookings: BookingConfirmation[] = [
    { id: "B-1001", bookingNo: "BK-2026-8891", guestName: "Emily Miller", roomType: "Deluxe Suite x 15", checkIn: "2026-07-10", checkOut: "2026-07-14", amount: 15600, status: "confirmed" },
    { id: "B-1002", bookingNo: "BK-2026-8892", guestName: "Yoga Group", roomType: "Superior Room x 10", checkIn: "2026-09-15", checkOut: "2026-09-18", amount: 8400, status: "pending" }
  ];

  reservations: ReservationStatus[] = [
    { id: "RS-1101", reservationNo: "RES-9801", guestName: "John Smith", roomType: "Deluxe Ocean View", status: "Checked-In", channel: "OTA", createdDate: "2026-06-11" },
    { id: "RS-1102", reservationNo: "RES-9802", guestName: "David Lee", roomType: "Standard Queen", status: "Confirmed", channel: "Direct", createdDate: "2026-06-12" },
    { id: "RS-1103", reservationNo: "RES-9803", guestName: "Anna Watson", roomType: "Executive Suite", status: "Checked-Out", channel: "Agent", createdDate: "2026-06-09" }
  ];

  handovers: OperationalHandover[] = [
    { id: "OH-1201", handoverNo: "HO-2026-092", fromDept: "Sales", toDept: "Banquet/F&B", date: "2026-06-12", status: "pending", notes: "Banquet setup sheet for Miller wedding group. Needs audio/mic config verified." },
    { id: "OH-1202", handoverNo: "HO-2026-091", fromDept: "Sales", toDept: "Front Office", date: "2026-06-10", status: "completed", notes: "Vip guest list for Finance Holdings corporate board members. Early check-in approved." }
  ];

  deposits: DepositPayment[] = [
    { id: "DP-1301", paymentNo: "PAY-5012", guestName: "Emily Miller", amount: 5000, method: "Transfer", status: "paid", date: "2026-06-08" },
    { id: "DP-1302", paymentNo: "PAY-5013", guestName: "Yoga Group", amount: 2000, method: "Card", status: "pending", date: "2026-06-12" }
  ];

  foHandovers: FrontOfficeHandover[] = [
    { id: "FOH-1401", shift: "Morning", agentName: "Alice Miller", date: "2026-06-12", status: "completed", notes: "Room 402 reported AC issue, engineering notified. Corporate group checking out at 12:00." },
    { id: "FOH-1402", shift: "Afternoon", agentName: "David Ten", date: "2026-06-12", status: "pending", notes: "Keycard machine in south wing is laggy. VIP guest in 501 requests late checkout tomorrow." }
  ];

  users: UserAccess[] = [
    { id: "U-1501", name: "John Doe", email: "j.doe@leadora-hotels.com", role: "MANAGER", status: "active", lastLogin: "2026-06-12 08:30" },
    { id: "U-1502", name: "Sarah Connor", email: "s.connor@leadora-hotels.com", role: "SALES", status: "active", lastLogin: "2026-06-12 09:05" },
    { id: "U-1503", name: "Admin Lead", email: "admin@leadora-hotels.com", role: "ADMIN", status: "active", lastLogin: "2026-06-11 15:40" }
  ];

  feedbacks: CustomerFeedback[] = [
    { id: "FB-1601", feedbackNo: "FB-101", guestName: "Bill Gateson", rating: 5, category: "Food", comment: "Excellent banquet dinner. The roasted lamb was perfectly cooked. Prompt service.", date: "2026-06-11" },
    { id: "FB-1602", feedbackNo: "FB-102", guestName: "Anna Watson", rating: 4, category: "Service", comment: "Helpful staff, check-in was smooth but checkout line was slightly long.", date: "2026-06-10" }
  ];
}

export const mockDb = new MockDatabase();
