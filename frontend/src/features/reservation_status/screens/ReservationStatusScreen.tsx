"use client";

import React, { useState, useMemo } from "react";
import { Search, User, CheckCircle2 } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
export type ReservationStatus = {
  id: string;
  guestName: string;
  reservationNo: string;
  roomType: string;
  createdDate: string;
  channel: string;
  status: string;
};

export function ReservationStatusScreen() {
  const [reservations, setReservations] = useState<ReservationStatus[]>([]);
  const [search, setSearch] = useState("");

  const filteredReservations = useMemo(() => {
    return reservations.filter(
      r =>
        r.guestName.toLowerCase().includes(search.toLowerCase()) ||
        r.reservationNo.toLowerCase().includes(search.toLowerCase()) ||
        r.roomType.toLowerCase().includes(search.toLowerCase())
    );
  }, [reservations, search]);

  const handleAction = (id: string, action: string) => {
    alert(`Reservation ID ${id} action '${action}' triggered. Status updated.`);
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Lodging & Rooms Status</h1>
          <p className="text-xs text-slate-400">Track current room occupancy, check-in dates, and guest folios</p>
        </div>
        <Badge variant="primary" className="text-xs px-2.5 font-bold uppercase bg-blue-100 text-blue-800">
          PMS Live Sync
        </Badge>
      </div>

      <Card className="border-slate-100 shadow-sm bg-white">
        <CardContent className="py-3 px-4">
          <div className="relative w-full md:w-72">
            <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search guest name, room number..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
            />
          </div>
        </CardContent>
      </Card>

      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
            <TableRow hoverable={false}>
              <TableHead className="font-semibold text-xs text-slate-500">Guest Name</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Reservation Ref</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Room Type</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Created Date</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Booking Channel</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Occupancy Status</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500 text-center">Desk Quick Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredReservations.length > 0 ? (
              filteredReservations.map(res => (
                <TableRow key={res.id} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-800">
                    <span className="flex items-center gap-1.5">
                      <User className="size-3.5 text-slate-400" />
                      {res.guestName}
                    </span>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-700">{res.reservationNo}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600">{res.roomType}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-500">{res.createdDate}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-500 font-bold uppercase">{res.channel}</TableCell>
                  <TableCell className="py-3 px-4">
                    <Badge
                      variant={
                        res.status === "Checked-In"
                          ? "success"
                          : res.status === "Confirmed"
                          ? "primary"
                          : "default"
                      }
                      size="sm"
                      className="font-bold text-[9px] uppercase"
                    >
                      {res.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-center">
                    {res.status === "Confirmed" ? (
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => handleAction(res.id, "checkin")}
                        className="px-2.5 py-1 text-[10px] font-bold bg-blue-600 hover:bg-blue-700 text-white"
                      >
                        Check In Guest
                      </Button>
                    ) : res.status === "Checked-In" ? (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleAction(res.id, "checkout")}
                        className="px-2.5 py-1 text-[10px] border-slate-200 text-red-600 hover:bg-red-50 hover:border-red-200 transition"
                      >
                        Check Out
                      </Button>
                    ) : (
                      <span className="text-[10px] text-slate-400 italic">Archived</span>
                    )}
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow hoverable={false}>
                <TableCell colSpan={7} className="py-8 text-center text-slate-400 text-xs">
                  No reservations matched your query.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
